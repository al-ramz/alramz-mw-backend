package com.alramz.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alramz.config.MmfProperties;
import com.alramz.exception.ApplicationException;
import com.alramz.exception.ErrorCode;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MmfServiceImplTest {

    private static final String DUP_REF_MSG = "ORA-00001: unique constraint (INSIGHT.UX_SWEEP_PROFIT_REF) violated";

    @Mock
    private MmfRepository repository;

    @Mock
    private PlatformTransactionManager brokTm;

    private MmfProperties properties;
    private MmfServiceImpl service;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        properties = new MmfProperties();
        properties.getCustodian().setClientIds(List.of("2001214", "2001215"));
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneId.of("Asia/Dubai"));
        service = new MmfServiceImpl(repository, properties, brokTm, clock);
        when(brokTm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(MmfServiceImpl.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(MmfServiceImpl.class)).detachAppender(logs);
    }

    // ---------- account ----------

    @Test
    void getAccount_found() {
        MmfAccount account = new MmfAccount().accountNumber("2939418");
        when(repository.findAccount(MmfBook.CAPITAL, "2939418")).thenReturn(Optional.of(account));

        assertThat(service.getAccount("2939418", MmfBook.CAPITAL)).isSameAs(account);
    }

    @Test
    void getAccount_notFound_shouldThrowMMF004() {
        when(repository.findAccount(MmfBook.MMF, "2939418")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccount("2939418", MmfBook.MMF))
                .isInstanceOf(MmfNotFoundException.class)
                .hasMessage("Account 2939418 not found in book MMF")
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_FOUND);
    }

    // ---------- sweeps ----------

    @Test
    void listSweeps_onlyOneDate_shouldThrow010or020() {
        assertThatThrownBy(() -> service.listSweeps("W", LocalDate.of(2025, 4, 1), null))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SWEEP_IN_DATES_INCOMPLETE)
                .hasMessage("Provide both fromDate and toDate, or neither");
        assertThatThrownBy(() -> service.listSweeps("O", null, LocalDate.of(2025, 4, 1)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SWEEP_OUT_DATES_INCOMPLETE);
        verifyNoInteractions(repository);
    }

    @Test
    void listSweeps_fromAfterTo_orOver93Days_shouldThrow012or022() {
        assertThatThrownBy(() -> service.listSweeps("W", LocalDate.of(2025, 4, 2), LocalDate.of(2025, 4, 1)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SWEEP_IN_RANGE_INVALID);
        // 2025-01-01 .. 2025-04-04 is 94 days inclusive
        assertThatThrownBy(() -> service.listSweeps("O", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 4)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SWEEP_OUT_RANGE_INVALID)
                .hasMessage("Date range must not exceed 93 days and fromDate must not be after toDate");
        verifyNoInteractions(repository);
    }

    @Test
    void listSweeps_93Days_shouldQueryBothBooksWithExclusiveUpperBound() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 4, 3); // 93 days inclusive
        when(repository.findSweeps(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(service.listSweeps("W", from, to)).isEmpty();

        verify(repository).findSweeps(MmfBook.CAPITAL, "W", from, LocalDate.of(2025, 4, 4));
        verify(repository).findSweeps(MmfBook.MMF, "W", from, LocalDate.of(2025, 4, 4));
    }

    @Test
    void listSweeps_noDates_shouldUseTodayPath() {
        when(repository.findSweeps(any(), any(), any(), any())).thenReturn(List.of());

        service.listSweeps("O", null, null);

        verify(repository).findSweeps(MmfBook.CAPITAL, "O", null, null);
        verify(repository).findSweeps(MmfBook.MMF, "O", null, null);
    }

    @Test
    void listSweeps_shouldMergeAndSortByDateThenSweepNo() {
        MmfSweep c1 = sweep(30L, "2025-04-02", MmfBook.CAPITAL);
        MmfSweep c2 = sweep(10L, "2025-04-03", MmfBook.CAPITAL);
        MmfSweep m1 = sweep(20L, "2025-04-02", MmfBook.MMF);
        MmfSweep m2 = sweep(5L, "2025-04-01", MmfBook.MMF);
        when(repository.findSweeps(eq(MmfBook.CAPITAL), any(), any(), any())).thenReturn(List.of(c1, c2));
        when(repository.findSweeps(eq(MmfBook.MMF), any(), any(), any())).thenReturn(List.of(m1, m2));

        assertThat(service.listSweeps("W", null, null)).containsExactly(m2, m1, c1, c2);
    }

    @Test
    void listSweeps_dbFailure_shouldPropagate() {
        when(repository.findSweeps(any(), any(), any(), any())).thenThrow(new CannotGetJdbcConnectionException("down"));

        assertThatThrownBy(() -> service.listSweeps("W", null, null)).isInstanceOf(CannotGetJdbcConnectionException.class);
    }

    // ---------- confirm ----------

    @Test
    void confirm_emptyOrTooMany_shouldThrowMMF030() {
        assertThatThrownBy(() -> service.confirmSweepIns(List.of()))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFIRM_ITEM_COUNT_INVALID)
                .hasMessage("Request must contain 1-1000 items");
        assertThatThrownBy(() -> service.confirmSweepIns(null))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFIRM_ITEM_COUNT_INVALID);
        List<MmfSweepConfirmationItem> tooMany = IntStream.rangeClosed(1, 1001)
                .mapToObj(i -> confirmItem(i, MmfBook.CAPITAL)).toList();
        assertThatThrownBy(() -> service.confirmSweepIns(tooMany))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFIRM_ITEM_COUNT_INVALID);
        verifyNoInteractions(repository, brokTm);
    }

    @Test
    void confirm_bothBooks_shouldCountWListIAndCommitOnce() {
        when(repository.lockSweepTypes(eq(MmfBook.CAPITAL), anyCollection())).thenReturn(Map.of(1L, "W", 2L, "W"));
        when(repository.lockSweepTypes(eq(MmfBook.MMF), anyCollection())).thenReturn(Map.of(2345L, "I"));
        when(repository.confirmSweepIns(MmfBook.CAPITAL, List.of(1L, 2L))).thenReturn(2);

        MmfSweepConfirmationResult result = service.confirmSweepIns(List.of(
                confirmItem(1, MmfBook.CAPITAL), confirmItem(2, MmfBook.CAPITAL), confirmItem(2345, MmfBook.MMF)));

        assertThat(result.getConfirmedCapital()).isEqualTo(2);
        assertThat(result.getConfirmedMmf()).isZero();
        assertThat(result.getAlreadyConfirmed()).containsExactly(new MmfSweepRef().sweepNo(2345L).book(MmfBook.MMF));
        verify(repository, never()).confirmSweepIns(eq(MmfBook.MMF), anyCollection());
        verify(brokTm).getTransaction(any());
        verify(brokTm).commit(any());
    }

    @Test
    void confirm_anyUnknownOrNotPending_shouldThrowMMF033AndRollBackWithoutUpdating() {
        when(repository.lockSweepTypes(eq(MmfBook.CAPITAL), anyCollection())).thenReturn(Map.of(1L, "W"));
        when(repository.lockSweepTypes(eq(MmfBook.MMF), anyCollection())).thenReturn(Map.of(7L, "O", 8L, "W"));

        assertThatThrownBy(() -> service.confirmSweepIns(List.of(
                confirmItem(1, MmfBook.CAPITAL), confirmItem(2170412, MmfBook.CAPITAL),
                confirmItem(7, MmfBook.MMF), confirmItem(8, MmfBook.MMF))))
                .isInstanceOf(MmfConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFIRM_CONFLICT)
                .hasMessage("Sweeps unknown or not pending: CAPITAL 2170412, MMF 7");

        verify(repository, never()).confirmSweepIns(any(), anyCollection());
        verify(brokTm).rollback(any());
        verify(brokTm, never()).commit(any());
    }

    @Test
    void confirm_singleBookConflict_typeP_shouldThrowMMF033() {
        when(repository.lockSweepTypes(eq(MmfBook.MMF), anyCollection())).thenReturn(Map.of(9L, "P"));

        assertThatThrownBy(() -> service.confirmSweepIns(List.of(confirmItem(9, MmfBook.MMF))))
                .hasMessage("Sweeps unknown or not pending: MMF 9");
        verify(brokTm).rollback(any());
    }

    @Test
    void confirm_allAlreadyConfirmed_shouldReturnZeroCounts() {
        when(repository.lockSweepTypes(eq(MmfBook.CAPITAL), anyCollection())).thenReturn(Map.of(1L, "I", 2L, "I"));

        MmfSweepConfirmationResult result = service.confirmSweepIns(List.of(
                confirmItem(1, MmfBook.CAPITAL), confirmItem(2, MmfBook.CAPITAL)));

        assertThat(result.getConfirmedCapital()).isZero();
        assertThat(result.getConfirmedMmf()).isZero();
        assertThat(result.getAlreadyConfirmed()).hasSize(2);
        verify(repository, never()).confirmSweepIns(any(), anyCollection());
        verify(brokTm).commit(any());
    }

    @Test
    void confirm_duplicateItems_shouldBeDeduplicated() {
        when(repository.lockSweepTypes(eq(MmfBook.MMF), anyCollection())).thenReturn(Map.of(5L, "W"));
        when(repository.confirmSweepIns(MmfBook.MMF, List.of(5L))).thenReturn(1);

        MmfSweepConfirmationResult result = service.confirmSweepIns(List.of(
                confirmItem(5, MmfBook.MMF), confirmItem(5, MmfBook.MMF)));

        assertThat(result.getConfirmedMmf()).isEqualTo(1);
        verify(repository).lockSweepTypes(MmfBook.MMF, List.of(5L));
    }

    @Test
    void confirm_lockFailsOnConnection_shouldPropagateForHandler() {
        when(repository.lockSweepTypes(any(), anyCollection())).thenThrow(new CannotGetJdbcConnectionException("down"));

        assertThatThrownBy(() -> service.confirmSweepIns(List.of(confirmItem(1, MmfBook.CAPITAL))))
                .isInstanceOf(CannotGetJdbcConnectionException.class);
        verify(brokTm).rollback(any());
    }

    // ---------- profit ----------

    @Test
    void profit_emptyOrTooMany_shouldThrowMMF040() {
        assertThatThrownBy(() -> service.createProfitDistributions(List.of()))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROFIT_ITEM_COUNT_INVALID)
                .hasMessage("Request must contain 1-5000 items");
        List<MmfProfitDistributionItem> tooMany = new ArrayList<>();
        for (int i = 0; i < 5001; i++) {
            tooMany.add(profitItem("1", MmfBook.CAPITAL));
        }
        assertThatThrownBy(() -> service.createProfitDistributions(tooMany))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROFIT_ITEM_COUNT_INVALID);
        verifyNoInteractions(repository);
    }

    @Test
    void profit_amountPrecision_shouldThrowMMF041BeforeAnyWrite() {
        MmfProfitDistributionItem fourDecimals = profitItem("2", MmfBook.CAPITAL).amount(new BigDecimal("1.2345"));
        assertThatThrownBy(() -> service.createProfitDistributions(List.of(profitItem("1", MmfBook.CAPITAL), fourDecimals)))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROFIT_ITEM_INVALID)
                .hasMessage("Item 2: amount must be > 0 with at most 3 decimals");

        MmfProfitDistributionItem tooBig = profitItem("3", MmfBook.CAPITAL).amount(new BigDecimal("12345678901234567890123"));
        assertThatThrownBy(() -> service.createProfitDistributions(List.of(tooBig)))
                .hasMessage("Item 1: amount must be > 0 with at most 3 decimals");

        MmfProfitDistributionItem zero = profitItem("4", MmfBook.CAPITAL).amount(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.createProfitDistributions(List.of(zero)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROFIT_ITEM_INVALID);
        verifyNoInteractions(repository);
    }

    @Test
    void profit_trailingZerosAndLargeValidAmount_shouldBeAccepted() {
        when(repository.nextSweepNo(any())).thenReturn(1L, 2L);

        List<MmfProfitDistributionResult> results = service.createProfitDistributions(List.of(
                profitItem("1", MmfBook.CAPITAL).amount(new BigDecimal("2500.7500")),
                profitItem("2", MmfBook.CAPITAL).amount(new BigDecimal("1234567890123456789012.123"))));

        assertThat(results).extracting(MmfProfitDistributionResult::getStatus).containsOnly(StatusEnum.CREATED);
    }

    @Test
    void profit_allInserted_shouldReturnResultsInRequestOrderWithSweepNos() {
        when(repository.nextSweepNo(MmfBook.CAPITAL)).thenReturn(2170513L);
        when(repository.nextSweepNo(MmfBook.MMF)).thenReturn(77L);
        MmfProfitDistributionItem first = profitItem("987654321", MmfBook.CAPITAL);
        MmfProfitDistributionItem second = profitItem("123456789", MmfBook.MMF);

        List<MmfProfitDistributionResult> results = service.createProfitDistributions(List.of(first, second));

        assertThat(results).containsExactly(
                new MmfProfitDistributionResult().referenceId("987654321").accountNumber("1005").book(MmfBook.CAPITAL)
                        .status(StatusEnum.CREATED).sweepNo(2170513L).errorCode(null),
                new MmfProfitDistributionResult().referenceId("123456789").accountNumber("1005").book(MmfBook.MMF)
                        .status(StatusEnum.CREATED).sweepNo(77L).errorCode(null));
        verify(repository).insertProfitDistribution(MmfBook.CAPITAL, 2170513L, first);
        verify(repository).insertProfitDistribution(MmfBook.MMF, 77L, second);
    }

    @Test
    void profit_duplicateRefPlusSuccess_shouldBeMixedWithMMF044() {
        when(repository.nextSweepNo(any())).thenReturn(1L, 2L);
        MmfProfitDistributionItem dup = profitItem("1", MmfBook.CAPITAL);
        doThrow(new DuplicateKeyException("insert", new SQLException(DUP_REF_MSG)))
                .when(repository).insertProfitDistribution(MmfBook.CAPITAL, 1L, dup);

        List<MmfProfitDistributionResult> results = service.createProfitDistributions(List.of(dup, profitItem("2", MmfBook.CAPITAL)));

        assertThat(results.get(0).getStatus()).isEqualTo(StatusEnum.FAILED);
        assertThat(results.get(0).getErrorCode()).isEqualTo("MMF044");
        assertThat(results.get(0).getSweepNo()).isNull();
        assertThat(results.get(1).getStatus()).isEqualTo(StatusEnum.CREATED);
    }

    @Test
    void profit_otherDuplicateOrDbErrorPlusSuccess_shouldBeMMF047WithoutRawText() {
        when(repository.nextSweepNo(any())).thenReturn(1L, 2L, 3L, 4L);
        MmfProfitDistributionItem pk = profitItem("1", MmfBook.CAPITAL);
        MmfProfitDistributionItem other = profitItem("2", MmfBook.CAPITAL);
        doThrow(new DuplicateKeyException("ORA-00001: unique constraint (INSIGHT.PK_SWEEP_FACILITY) violated"))
                .when(repository).insertProfitDistribution(eq(MmfBook.CAPITAL), anyLong(), eq(pk));
        doThrow(new BadSqlGrammarException("insert", "SQL", new SQLException("ORA-00904: invalid identifier")))
                .when(repository).insertProfitDistribution(MmfBook.CAPITAL, 3L, other);

        List<MmfProfitDistributionResult> results = service.createProfitDistributions(
                List.of(pk, other, profitItem("3", MmfBook.CAPITAL)));

        assertThat(results).extracting(MmfProfitDistributionResult::getErrorCode).containsExactly("MMF047", "MMF047", null);
        assertThat(results.toString()).doesNotContain("ORA-");
    }

    @Test
    void profit_sweepNoCollision_shouldRetryOnceWithFreshMax() {
        when(repository.nextSweepNo(MmfBook.CAPITAL)).thenReturn(5L, 6L);
        MmfProfitDistributionItem item = profitItem("1", MmfBook.CAPITAL);
        doThrow(new DuplicateKeyException("ORA-00001: unique constraint (INSIGHT.PK_SWEEP_FACILITY) violated"))
                .when(repository).insertProfitDistribution(MmfBook.CAPITAL, 5L, item);

        List<MmfProfitDistributionResult> results = service.createProfitDistributions(List.of(item));

        assertThat(results.get(0).getStatus()).isEqualTo(StatusEnum.CREATED);
        assertThat(results.get(0).getSweepNo()).isEqualTo(6L);
        verify(repository).insertProfitDistribution(MmfBook.CAPITAL, 6L, item);
    }

    @Test
    void profit_duplicateRef_shouldNotRetry() {
        when(repository.nextSweepNo(any())).thenReturn(1L, 2L);
        MmfProfitDistributionItem dup = profitItem("1", MmfBook.CAPITAL);
        doThrow(new DuplicateKeyException("insert", new SQLException(DUP_REF_MSG)))
                .when(repository).insertProfitDistribution(MmfBook.CAPITAL, 1L, dup);

        assertThatThrownBy(() -> service.createProfitDistributions(List.of(dup))).isInstanceOf(MmfConflictException.class);
        verify(repository, times(1)).nextSweepNo(MmfBook.CAPITAL);
    }

    @Test
    void profit_allDuplicates_shouldThrowMMF044ListingRefs() {
        when(repository.nextSweepNo(any())).thenReturn(1L, 2L);
        doThrow(new DuplicateKeyException("x", new SQLException(DUP_REF_MSG.toLowerCase())))
                .when(repository).insertProfitDistribution(any(), anyLong(), any());

        assertThatThrownBy(() -> service.createProfitDistributions(List.of(
                profitItem("987654321", MmfBook.CAPITAL), profitItem("123456789", MmfBook.MMF))))
                .isInstanceOf(MmfConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROFIT_DUPLICATE_REFERENCE)
                .hasMessage("referenceId already booked: CAPITAL 987654321, MMF 123456789");
    }

    @Test
    void profit_allNonConnectivityFailures_shouldThrowMMF045() {
        when(repository.nextSweepNo(any())).thenThrow(new QueryTimeoutException("slow"));

        assertThatThrownBy(() -> service.createProfitDistributions(List.of(profitItem("1", MmfBook.CAPITAL))))
                .isInstanceOf(MmfBackendException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROFIT_INTERNAL_ERROR)
                .hasMessage("Internal Server Error");
    }

    @Test
    void profit_bookUnreachableOnFirstItem_shouldThrowMMF046AndNotRetryThatBook() {
        when(repository.nextSweepNo(MmfBook.CAPITAL)).thenThrow(new CannotGetJdbcConnectionException("down"));

        assertThatThrownBy(() -> service.createProfitDistributions(List.of(
                profitItem("1", MmfBook.CAPITAL), profitItem("2", MmfBook.CAPITAL), profitItem("3", MmfBook.CAPITAL))))
                .isInstanceOf(MmfBackendUnavailableException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROFIT_UNAVAILABLE)
                .hasMessage("MMF data source unavailable");

        verify(repository, times(1)).nextSweepNo(MmfBook.CAPITAL);
        verify(repository, never()).insertProfitDistribution(any(), anyLong(), any());
    }

    @Test
    void profit_bookUnreachableMidBatch_shouldFailRestOfThatBookWithoutAttempt() {
        when(repository.nextSweepNo(MmfBook.CAPITAL)).thenReturn(1L).thenThrow(new CannotGetJdbcConnectionException("down"));
        when(repository.nextSweepNo(MmfBook.MMF)).thenReturn(50L);

        List<MmfProfitDistributionResult> results = service.createProfitDistributions(List.of(
                profitItem("1", MmfBook.CAPITAL), profitItem("2", MmfBook.CAPITAL),
                profitItem("3", MmfBook.MMF), profitItem("4", MmfBook.CAPITAL)));

        assertThat(results).extracting(MmfProfitDistributionResult::getStatus)
                .containsExactly(StatusEnum.CREATED, StatusEnum.FAILED, StatusEnum.CREATED, StatusEnum.FAILED);
        assertThat(results).extracting(MmfProfitDistributionResult::getErrorCode)
                .containsExactly(null, "MMF047", null, "MMF047");
        verify(repository, times(2)).nextSweepNo(MmfBook.CAPITAL);
    }

    // ---------- custodian ----------

    @Test
    void custodianCash_shouldDeduplicateIdsBeforeRepositoryCall() {
        List<MmfCustodianCash> rows = List.of(new MmfCustodianCash().clientId("2001214"));
        when(repository.findCustodianCash(List.of("2001214", "2001215"))).thenReturn(rows);

        assertThat(service.listCustodianCash(List.of("2001214", "2001215", "2001214"))).isSameAs(rows);
    }

    @Test
    void custodianPositions_futureDate_shouldThrowMMF060() {
        assertThatThrownBy(() -> service.listCustodianPositions(LocalDate.of(2026, 10, 20)))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POSITIONS_AS_OF_DATE_INVALID)
                .hasMessage("asOfDate must be an ISO-8601 date not in the future");
        verifyNoInteractions(repository);
    }

    @Test
    void custodianPositions_today_shouldBeAccepted() {
        service.listCustodianPositions(LocalDate.of(2026, 10, 19));

        verify(repository).findCustodianPositions(List.of("2001214", "2001215"), LocalDate.of(2026, 10, 19));
    }

    @Test
    void custodianPositions_emptyOrNullConfig_shouldThrowMMF065WithoutRepositoryCall() {
        properties.getCustodian().setClientIds(List.of());
        assertThatThrownBy(() -> service.listCustodianPositions(null))
                .isInstanceOf(MmfBackendException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POSITIONS_CLIENTS_NOT_CONFIGURED)
                .hasMessage("Custodian client list is not configured");

        properties.getCustodian().setClientIds(null);
        assertThatThrownBy(() -> service.listCustodianPositions(null))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POSITIONS_CLIENTS_NOT_CONFIGURED);
        verifyNoInteractions(repository);
    }

    @Test
    void custodianPositions_nullDate_shouldPassNullAndConfiguredIds() {
        List<MmfCustodianPosition> rows = List.of(new MmfCustodianPosition().clientId("2001214"));
        when(repository.findCustodianPositions(any(Collection.class), eq(null))).thenReturn(rows);

        assertThat(service.listCustodianPositions(null)).isSameAs(rows);
        verify(repository).findCustodianPositions(List.of("2001214", "2001215"), null);
    }

    // ---------- helpers ----------

    private static MmfSweep sweep(long no, String date, MmfBook book) {
        return new MmfSweep().sweepNo(no).date(LocalDate.parse(date)).accountNumber("1").amount(BigDecimal.ONE).source(book);
    }

    private static MmfSweepConfirmationItem confirmItem(long sweepNo, MmfBook book) {
        return new MmfSweepConfirmationItem().sweepNo(sweepNo).source(book);
    }

    private static MmfProfitDistributionItem profitItem(String referenceId, MmfBook book) {
        return new MmfProfitDistributionItem().sweepDate(LocalDate.of(2025, 4, 20)).accountNumber("1005")
                .amount(new BigDecimal("2500.75")).referenceId(referenceId).source(book);
    }
}
