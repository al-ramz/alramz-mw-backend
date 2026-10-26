package com.alramz.service.impl;

import com.alramz.config.MmfProperties;
import com.alramz.exception.ApplicationException;
import com.alramz.exception.ErrorCode;
import com.alramz.exception.GlobalExceptionHandler;
import com.alramz.exception.MmfBackendException;
import com.alramz.exception.MmfBackendUnavailableException;
import com.alramz.exception.MmfConflictException;
import com.alramz.exception.MmfNotFoundException;
import com.alramz.model.MmfAccount;
import com.alramz.model.MmfBook;
import com.alramz.model.MmfCustodianCash;
import com.alramz.model.MmfCustodianPosition;
import com.alramz.model.MmfProfitDistributionItem;
import com.alramz.model.MmfProfitDistributionResult;
import com.alramz.model.MmfProfitDistributionResult.StatusEnum;
import com.alramz.model.MmfSweep;
import com.alramz.model.MmfSweepConfirmationItem;
import com.alramz.model.MmfSweepConfirmationResult;
import com.alramz.model.MmfSweepRef;
import com.alramz.repository.MmfRepository;
import com.alramz.service.MmfService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MMF business rules. Oracle work never uses a bare @Transactional (the @Primary TM is middleware Postgres):
 * both books live on brok, so all Oracle work runs through one TransactionTemplate over brokTransactionManager.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "company.datasource", name = "brok.enabled", havingValue = "true")
public class MmfServiceImpl implements MmfService {

    static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");

    private static final int MAX_RANGE_DAYS = 93;
    private static final int MAX_CONFIRMATIONS = 1000;
    private static final int MAX_PROFIT_ITEMS = 5000;
    private static final int MAX_AMOUNT_SCALE = 3;
    private static final int MAX_AMOUNT_INTEGER_DIGITS = 22;
    private static final String REF_INDEX = "UX_SWEEP_PROFIT_REF";
    private static final String PENDING = "W";
    private static final String CONFIRMED = "I";
    private static final String INTERNAL_MSG = "Internal Server Error";
    private static final String UNAVAILABLE_MSG = "MMF data source unavailable";

    private final MmfRepository repository;
    private final MmfProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Autowired
    public MmfServiceImpl(MmfRepository repository, MmfProperties properties,
                          @Qualifier("brokTransactionManager") PlatformTransactionManager brokTransactionManager) {
        this(repository, properties, brokTransactionManager, Clock.system(DUBAI));
    }

    MmfServiceImpl(MmfRepository repository, MmfProperties properties,
                   PlatformTransactionManager brokTransactionManager, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.transaction = new TransactionTemplate(brokTransactionManager);
        this.clock = clock;
    }

    @Override
    public MmfAccount getAccount(String accountNumber, MmfBook book) {
        return repository.findAccount(book, accountNumber).orElseThrow(() -> new MmfNotFoundException(
                ErrorCode.ACCOUNT_NOT_FOUND, "Account " + accountNumber + " not found in book " + book));
    }

    @Override
    public List<MmfSweep> listSweeps(String sweepType, LocalDate fromDate, LocalDate toDate) {
        boolean sweepIn = SWEEP_IN.equals(sweepType);
        if ((fromDate == null) != (toDate == null)) {
            ErrorCode code = sweepIn ? ErrorCode.SWEEP_IN_DATES_INCOMPLETE : ErrorCode.SWEEP_OUT_DATES_INCOMPLETE;
            throw new ApplicationException(code, code.defaultMessage());
        }
        LocalDate toDateExclusive = null;
        if (fromDate != null) {
            if (fromDate.isAfter(toDate) || ChronoUnit.DAYS.between(fromDate, toDate) + 1 > MAX_RANGE_DAYS) {
                ErrorCode code = sweepIn ? ErrorCode.SWEEP_IN_RANGE_INVALID : ErrorCode.SWEEP_OUT_RANGE_INVALID;
                throw new ApplicationException(code, code.defaultMessage());
            }
            toDateExclusive = toDate.plusDays(1);
        }
        List<MmfSweep> sweeps = new ArrayList<>();
        for (MmfBook book : MmfBook.values()) {
            sweeps.addAll(repository.findSweeps(book, sweepType, fromDate, toDateExclusive));
        }
        sweeps.sort(Comparator.comparing(MmfSweep::getDate).thenComparing(MmfSweep::getSweepNo));
        return sweeps;
    }

    /**
     * All-or-nothing in one brok transaction: every book is locked (CAPITAL first, fixed lock order) and
     * validated before any UPDATE runs.
     */
    @Override
    public MmfSweepConfirmationResult confirmSweepIns(List<MmfSweepConfirmationItem> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_CONFIRMATIONS) {
            throw new ApplicationException(ErrorCode.CONFIRM_ITEM_COUNT_INVALID, ErrorCode.CONFIRM_ITEM_COUNT_INVALID.defaultMessage());
        }
        Map<MmfBook, List<Long>> byBook = new EnumMap<>(MmfBook.class);
        for (MmfSweepConfirmationItem item : items) {
            List<Long> numbers = byBook.computeIfAbsent(item.getSource(), b -> new ArrayList<>());
            if (!numbers.contains(item.getSweepNo())) {
                numbers.add(item.getSweepNo());
            }
        }
        Map<MmfBook, Map<Long, String>> locked = new EnumMap<>(MmfBook.class);
        MmfSweepConfirmationResult result = new MmfSweepConfirmationResult()
                .confirmedCapital(0).confirmedMmf(0).alreadyConfirmed(new ArrayList<>());
        transaction.executeWithoutResult(status -> {
            byBook.forEach((book, numbers) -> locked.put(book, repository.lockSweepTypes(book, numbers)));
            validateAndConfirm(byBook, locked, result);
        });
        return result;
    }

    private void validateAndConfirm(Map<MmfBook, List<Long>> byBook, Map<MmfBook, Map<Long, String>> locked,
                                    MmfSweepConfirmationResult result) {
        List<String> conflicts = new ArrayList<>();
        byBook.forEach((book, numbers) -> numbers.forEach(no -> {
            String type = locked.get(book).get(no);
            if (!PENDING.equals(type) && !CONFIRMED.equals(type)) {
                conflicts.add(book + " " + no);
            }
        }));
        if (!conflicts.isEmpty()) {
            throw new MmfConflictException(ErrorCode.CONFIRM_CONFLICT,
                    "Sweeps unknown or not pending: " + String.join(", ", conflicts));
        }
        byBook.forEach((book, numbers) -> {
            List<Long> pending = new ArrayList<>();
            for (Long no : numbers) {
                if (PENDING.equals(locked.get(book).get(no))) {
                    pending.add(no);
                } else {
                    result.getAlreadyConfirmed().add(new MmfSweepRef().sweepNo(no).book(book));
                }
            }
            int confirmed = pending.isEmpty() ? 0 : repository.confirmSweepIns(book, pending);
            if (book == MmfBook.CAPITAL) {
                result.setConfirmedCapital(confirmed);
            } else {
                result.setConfirmedMmf(confirmed);
            }
        });
    }

    /**
     * Each item is its own autocommit unit in request order. The DB unique index UX_SWEEP_PROFIT_REF is the
     * idempotency guard; a book that becomes unreachable is not attempted again for the rest of the batch.
     */
    @Override
    public List<MmfProfitDistributionResult> createProfitDistributions(List<MmfProfitDistributionItem> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_PROFIT_ITEMS) {
            throw new ApplicationException(ErrorCode.PROFIT_ITEM_COUNT_INVALID, ErrorCode.PROFIT_ITEM_COUNT_INVALID.defaultMessage());
        }
        for (int i = 0; i < items.size(); i++) {
            if (!isValidAmount(items.get(i).getAmount())) {
                throw new ApplicationException(ErrorCode.PROFIT_ITEM_INVALID,
                        "Item " + (i + 1) + ": " + ErrorCode.PROFIT_ITEM_INVALID.defaultMessage());
            }
        }
        Set<MmfBook> unreachable = EnumSet.noneOf(MmfBook.class);
        List<MmfProfitDistributionResult> results = new ArrayList<>(items.size());
        Boolean firstFailureUnavailable = null;
        for (MmfProfitDistributionItem item : items) {
            MmfBook book = item.getSource();
            MmfProfitDistributionResult result = new MmfProfitDistributionResult()
                    .referenceId(item.getReferenceId()).accountNumber(item.getAccountNumber()).book(book);
            results.add(result);
            if (unreachable.contains(book)) {
                fail(result, ErrorCode.PROFIT_ITEM_FAILED);
                continue;
            }
            try {
                long sweepNo = insertProfit(book, item);
                result.status(StatusEnum.CREATED).sweepNo(sweepNo).errorCode(null);
            } catch (DuplicateKeyException e) {
                boolean refDuplicate = isReferenceDuplicate(e);
                log.warn("Profit distribution {} {} rejected as duplicate ({}): {}", book, item.getReferenceId(),
                        refDuplicate ? "MMF044" : "MMF047", NestedExceptionUtils.getMostSpecificCause(e).getMessage());
                fail(result, refDuplicate ? ErrorCode.PROFIT_DUPLICATE_REFERENCE : ErrorCode.PROFIT_ITEM_FAILED);
            } catch (DataAccessException e) {
                boolean bookDown = GlobalExceptionHandler.isResourceFailure(e);
                if (bookDown) {
                    unreachable.add(book);
                }
                log.error("Profit distribution {} {} failed{}", book, item.getReferenceId(),
                        bookDown ? "; book unreachable, skipping its remaining items" : "", e);
                fail(result, ErrorCode.PROFIT_ITEM_FAILED);
            }
            if (firstFailureUnavailable == null && result.getStatus() == StatusEnum.FAILED) {
                firstFailureUnavailable = unreachable.contains(book);
            }
        }
        return aggregate(results, Boolean.TRUE.equals(firstFailureUnavailable));
    }

    private static List<MmfProfitDistributionResult> aggregate(List<MmfProfitDistributionResult> results,
                                                               boolean firstFailureUnavailable) {
        if (results.stream().anyMatch(r -> r.getStatus() == StatusEnum.CREATED)) {
            return results; // all created (201) or mixed (207)
        }
        String duplicate = ErrorCode.PROFIT_DUPLICATE_REFERENCE.code();
        if (results.stream().allMatch(r -> duplicate.equals(r.getErrorCode()))) {
            throw new MmfConflictException(ErrorCode.PROFIT_DUPLICATE_REFERENCE, "referenceId already booked: "
                    + results.stream().map(r -> r.getBook() + " " + r.getReferenceId()).collect(Collectors.joining(", ")));
        }
        if (firstFailureUnavailable) {
            throw new MmfBackendUnavailableException(ErrorCode.PROFIT_UNAVAILABLE, UNAVAILABLE_MSG, null);
        }
        throw new MmfBackendException(ErrorCode.PROFIT_INTERNAL_ERROR, INTERNAL_MSG, null);
    }

    /** MAX+1 numbering can collide with a concurrent writer on the SWEEP_NO PK; re-read MAX and retry once. */
    private long insertProfit(MmfBook book, MmfProfitDistributionItem item) {
        long sweepNo = repository.nextSweepNo(book);
        try {
            repository.insertProfitDistribution(book, sweepNo, item);
            return sweepNo;
        } catch (DuplicateKeyException e) {
            if (isReferenceDuplicate(e)) {
                throw e;
            }
            log.warn("Profit distribution {} {} collided on SWEEP_NO {}; retrying once", book, item.getReferenceId(), sweepNo);
        }
        sweepNo = repository.nextSweepNo(book);
        repository.insertProfitDistribution(book, sweepNo, item);
        return sweepNo;
    }

    private static void fail(MmfProfitDistributionResult result, ErrorCode code) {
        result.status(StatusEnum.FAILED).sweepNo(null).errorCode(code.code());
    }

    private static boolean isReferenceDuplicate(DuplicateKeyException e) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return message != null && message.toUpperCase(Locale.ROOT).contains(REF_INDEX);
    }

    /** NUMBER(25,3): positive, at most 3 decimals and 22 integer digits. */
    private static boolean isValidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return false;
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() <= MAX_AMOUNT_SCALE && stripped.precision() - stripped.scale() <= MAX_AMOUNT_INTEGER_DIGITS;
    }

    @Override
    public List<MmfCustodianCash> listCustodianCash(List<String> clientIds) {
        return repository.findCustodianCash(new ArrayList<>(new LinkedHashSet<>(clientIds)));
    }

    @Override
    public List<MmfCustodianPosition> listCustodianPositions(LocalDate asOfDate) {
        if (asOfDate != null && asOfDate.isAfter(LocalDate.now(clock))) {
            throw new ApplicationException(ErrorCode.POSITIONS_AS_OF_DATE_INVALID, ErrorCode.POSITIONS_AS_OF_DATE_INVALID.defaultMessage());
        }
        List<String> clientIds = properties.getCustodian().getClientIds();
        if (clientIds == null || clientIds.isEmpty()) {
            throw new MmfBackendException(ErrorCode.POSITIONS_CLIENTS_NOT_CONFIGURED,
                    ErrorCode.POSITIONS_CLIENTS_NOT_CONFIGURED.defaultMessage(), null);
        }
        return repository.findCustodianPositions(clientIds, asOfDate);
    }
}
