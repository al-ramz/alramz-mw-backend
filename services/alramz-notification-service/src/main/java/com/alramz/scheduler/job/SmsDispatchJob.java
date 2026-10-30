package com.alramz.scheduler.job;

import com.alramz.config.SmsProperties;
import com.alramz.exception.SmsException;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.SmsRecipientResult;
import com.alramz.model.SmsSendRequest;
import com.alramz.repository.SmsTableRepository;
import com.alramz.repository.SmsTableRepository.SmsTableRow;
import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.service.Schedulable;
import com.alramz.service.SmsService;
import com.alramz.service.impl.SmsServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Sends pending INSIGHT.SMS_TABLE rows (spec 3.4), replacing the legacy webMethods listener. Scheduled by the
 * starter scheduler (schedule_job row notification/smsDispatch, FIXED_DELAY, so it never overlaps itself in a
 * pod); the claim makes batches disjoint across pods. Off unless app.sms.dispatch.enabled=true. Never throws.
 */
@Slf4j
@Component("smsDispatchJobRunner")
public class SmsDispatchJob implements Schedulable {

    private static final int MAX_MESSAGE_ID_LENGTH = 65;

    private final ObjectProvider<SmsTableRepository> repositoryProvider;
    private final SmsService smsService;
    private final SmsProperties properties;
    private final Clock clock;

    @Autowired
    public SmsDispatchJob(final ObjectProvider<SmsTableRepository> repositoryProvider, final SmsService smsService,
                          final SmsProperties properties) {
        this(repositoryProvider, smsService, properties, Clock.system(ZoneId.of("Asia/Dubai")));
    }

    SmsDispatchJob(final ObjectProvider<SmsTableRepository> repositoryProvider, final SmsService smsService,
                   final SmsProperties properties, final Clock clock) {
        if (properties.dispatch().enabled() && repositoryProvider.getIfAvailable() == null) {
            throw new IllegalStateException("app.sms.dispatch.enabled=true requires company.datasource.brok.enabled=true");
        }
        this.repositoryProvider = repositoryProvider;
        this.smsService = smsService;
        this.properties = properties;
        this.clock = clock;
    }

    private enum RowOutcome { SENT, FAILED, INVALID }

    @Override
    public void run(final ScheduleInfoBean scheduleInfoBean) {
        final SmsProperties.Dispatch dispatch = properties.dispatch();
        if (!dispatch.enabled()) {
            return;
        }
        final long start = clock.millis();
        try {
            final SmsTableRepository repository = repositoryProvider.getObject();
            final SmsProperties.Status status = dispatch.status();
            final List<SmsTableRow> rows = repository.claimPending(dispatch.batchSize(), status.pending(), status.inProgress());
            int sent = 0;
            int failed = 0;
            int invalid = 0;
            for (SmsTableRow row : rows) {
                final RowOutcome outcome = process(repository, row, status);
                if (outcome == RowOutcome.SENT) {
                    sent++;
                } else if (outcome == RowOutcome.INVALID) {
                    invalid++;
                } else {
                    failed++;
                }
            }
            log.info("SMS dispatch run claimed={} sent={} failed={} invalid={} durationMs={}", rows.size(), sent, failed,
                    invalid, clock.millis() - start);
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException: a scheduled run must never throw
            log.error("SMS dispatch run failed error={}", e.getClass().getSimpleName());
        }
    }

    private RowOutcome process(final SmsTableRepository repository, final SmsTableRow row, final SmsProperties.Status status) {
        final String correlationId = UUID.randomUUID().toString();
        MDCUtil.putCorrelationId(correlationId);
        final String masked = SmsServiceImpl.mask(row.mobileNo());
        try {
            final LocalDateTime now = LocalDateTime.now(clock);
            if (row.mobileNo() == null) {
                return invalid(repository, row, status, now, masked);
            }
            final SmsRecipientResult result;
            final SmsService.Outcome outcome;
            try {
                outcome = smsService.sendWithOutcome(new SmsSendRequest()
                        .messageBody(row.messageBody())
                        .mobileNumbers(List.of(row.mobileNo()))
                        .msgCategory(properties.defaults().msgCategory())
                        .expiryInSecs(properties.defaults().expiryInSecs()));
                result = outcome.result().getResults().get(0);
            } catch (SmsException e) {
                if ("SMS002".equals(e.getCode())) {
                    return invalid(repository, row, status, now, masked);
                }
                return unexpected(repository, row, status, now, correlationId, masked, e);
            } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException: one bad row must not stop the batch
                return unexpected(repository, row, status, now, correlationId, masked, e);
            }
            final String provider = result.getServiceProvider().getValue();
            if (result.getStatus() == SmsRecipientResult.StatusEnum.SENT) {
                repository.markResult(row.serialNo(), SmsProperties.STATUS_SENT,
                        truncate("200|" + result.getMessageId() + "|" + correlationId + "|" + provider), now, true,
                        status.inProgress());
                log.info("SMS dispatch row serialNo={} provider={} outcome=SENT number={}", row.serialNo(), provider, masked);
                return RowOutcome.SENT;
            }
            final String code = outcome.anyProviderRejected() ? "500" : "503";
            repository.markResult(row.serialNo(), SmsProperties.STATUS_FAILED,
                    truncate(code + "|" + correlationId + "|" + provider), now, true, status.inProgress());
            log.warn("SMS dispatch row serialNo={} provider={} outcome=FAILED code={} number={}", row.serialNo(), provider,
                    code, masked);
            return RowOutcome.FAILED;
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException: e.g. brok down during markResult
            log.error("SMS dispatch row serialNo={} not updated error={}", row.serialNo(), e.getClass().getSimpleName());
            return RowOutcome.FAILED;
        } finally {
            MDCUtil.clear();
        }
    }

    private static RowOutcome invalid(final SmsTableRepository repository, final SmsTableRow row,
                                      final SmsProperties.Status status, final LocalDateTime now, final String masked) {
        repository.markResult(row.serialNo(), status.invalidMobile(), null, now, false, status.inProgress());
        log.warn("SMS dispatch row serialNo={} outcome=INVALID_MOBILE number={}", row.serialNo(), masked);
        return RowOutcome.INVALID;
    }

    private static RowOutcome unexpected(final SmsTableRepository repository, final SmsTableRow row,
                                         final SmsProperties.Status status, final LocalDateTime now,
                                         final String correlationId, final String masked, final RuntimeException e) {
        log.error("SMS dispatch row serialNo={} outcome=ERROR number={} error={}", row.serialNo(), masked,
                e.getClass().getSimpleName());
        repository.markResult(row.serialNo(), SmsProperties.STATUS_FAILED, truncate("503|" + correlationId + "|NONE"),
                now, true, status.inProgress());
        return RowOutcome.FAILED;
    }

    private static String truncate(final String messageId) {
        return messageId.length() <= MAX_MESSAGE_ID_LENGTH ? messageId : messageId.substring(0, MAX_MESSAGE_ID_LENGTH);
    }
}
