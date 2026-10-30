package com.alramz.scheduler.job;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alramz.config.SmsProperties;
import com.alramz.exception.SmsException;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.SmsRecipientResult;
import com.alramz.model.SmsSendRequest;
import com.alramz.model.SmsSendResult;
import com.alramz.repository.SmsTableRepository;
import com.alramz.repository.SmsTableRepository.SmsTableRow;
import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.service.SmsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmsDispatchJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneId.of("Asia/Dubai"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 14, 0);
    private static final int PENDING = 10;
    private static final int IN_PROGRESS = 20;
    private static final int INVALID = 30;
    private static final String NUMBER = "971500000010";

    @Mock
    private ObjectProvider<SmsTableRepository> repositoryProvider;
    @Mock
    private SmsTableRepository repository;
    @Mock
    private SmsService smsService;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        when(repositoryProvider.getIfAvailable()).thenReturn(repository);
        when(repositoryProvider.getObject()).thenReturn(repository);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(SmsDispatchJob.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(SmsDispatchJob.class)).detachAppender(logs);
        MDC.clear();
    }

    private static SmsProperties properties(final boolean enabled) {
        return new SmsProperties(false, "WASELNEY", 50, 1500, null, null, new SmsProperties.Defaults("4.2", 300),
                null, null, null, new SmsProperties.Dispatch(enabled, 25,
                enabled ? new SmsProperties.Status(PENDING, IN_PROGRESS, INVALID) : null));
    }

    private SmsDispatchJob job(final boolean enabled) {
        return new SmsDispatchJob(repositoryProvider, smsService, properties(enabled), CLOCK);
    }

    private static SmsService.Outcome outcome(final SmsRecipientResult.StatusEnum status, final String messageId,
                                              final boolean rejected) {
        return new SmsService.Outcome(new SmsSendResult().correlationId("x").simulated(false).results(List.of(
                new SmsRecipientResult().mobileNumber(NUMBER).serviceProvider(SmsRecipientResult.ServiceProviderEnum.WASELNEY)
                        .status(status).messageId(messageId))), rejected);
    }

    private void givenRows(final SmsTableRow... rows) {
        when(repository.claimPending(25, PENDING, IN_PROGRESS)).thenReturn(List.of(rows));
    }

    private static SmsTableRow row(final long serial, final String mobile) {
        return new SmsTableRow(serial, "Statement ready", mobile, 0);
    }

    @Test
    void e38_disabledRunNeverTouchesTheRepository() {
        final SmsDispatchJob job = new SmsDispatchJob(repositoryProvider, smsService, properties(false), CLOCK);

        job.run(new ScheduleInfoBean());

        verifyNoInteractions(repositoryProvider, smsService);
    }

    @Test
    void e44_enabledWithoutBrokFailsAtStartup() {
        when(repositoryProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> job(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("company.datasource.brok.enabled=true");
    }

    @Test
    void e41_sentRowGetsStatus88AndProviderMessageId() {
        givenRows(row(1L, NUMBER));
        when(smsService.sendWithOutcome(any())).thenReturn(outcome(SmsRecipientResult.StatusEnum.SENT, "88123456", false));

        job(true).run(new ScheduleInfoBean());

        final ArgumentCaptor<SmsSendRequest> request = ArgumentCaptor.forClass(SmsSendRequest.class);
        verify(smsService).sendWithOutcome(request.capture());
        assertThat(request.getValue().getMessageBody()).isEqualTo("Statement ready");
        assertThat(request.getValue().getMobileNumbers()).containsExactly(NUMBER);
        assertThat(request.getValue().getMsgCategory()).isEqualTo("4.2");
        assertThat(request.getValue().getExpiryInSecs()).isEqualTo(300);
        final ArgumentCaptor<String> messageId = ArgumentCaptor.forClass(String.class);
        verify(repository).markResult(eq(1L), eq(88), messageId.capture(), eq(NOW), eq(true), eq(IN_PROGRESS));
        assertThat(messageId.getValue()).matches("200\\|88123456\\|[0-9a-f-]{36}\\|WASELNEY");
        assertThat(MDCUtil.getCorrelationId()).isNull();
    }

    @Test
    void e41_failedRowGets500OnRejectionAnd503OnTransport() {
        givenRows(row(1L, NUMBER), row(2L, NUMBER));
        when(smsService.sendWithOutcome(any()))
                .thenReturn(outcome(SmsRecipientResult.StatusEnum.FAILED, null, true))
                .thenReturn(outcome(SmsRecipientResult.StatusEnum.FAILED, null, false));

        job(true).run(new ScheduleInfoBean());

        verify(repository).markResult(eq(1L), eq(1), startsWith("500|"), eq(NOW), eq(true), eq(IN_PROGRESS));
        verify(repository).markResult(eq(2L), eq(1), startsWith("503|"), eq(NOW), eq(true), eq(IN_PROGRESS));
    }

    @Test
    void e40_invalidMobileGetsInvalidStatusWithoutAttempt() {
        givenRows(row(1L, "12"), row(2L, null));
        when(smsService.sendWithOutcome(any())).thenThrow(new SmsException("SMS002", "Invalid mobile number in: mobileNumbers[1]"));

        job(true).run(new ScheduleInfoBean());

        verify(repository).markResult(eq(1L), eq(INVALID), isNull(), eq(NOW), eq(false), eq(IN_PROGRESS));
        verify(repository).markResult(eq(2L), eq(INVALID), isNull(), eq(NOW), eq(false), eq(IN_PROGRESS));
    }

    @Test
    void e43_rowExceptionMarksFailedAndOtherRowsContinue() {
        givenRows(row(1L, NUMBER), row(2L, NUMBER));
        when(smsService.sendWithOutcome(any()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(outcome(SmsRecipientResult.StatusEnum.SENT, "m2", false));

        job(true).run(new ScheduleInfoBean());

        final ArgumentCaptor<String> messageId = ArgumentCaptor.forClass(String.class);
        verify(repository).markResult(eq(1L), eq(1), messageId.capture(), eq(NOW), eq(true), eq(IN_PROGRESS));
        assertThat(messageId.getValue()).matches("503\\|[0-9a-f-]{36}\\|NONE");
        verify(repository).markResult(eq(2L), eq(88), startsWith("200|m2|"), eq(NOW), eq(true), eq(IN_PROGRESS));
    }

    @Test
    void e43_markResultFailureDoesNotStopTheBatch() {
        givenRows(row(1L, NUMBER), row(2L, NUMBER));
        when(smsService.sendWithOutcome(any())).thenReturn(outcome(SmsRecipientResult.StatusEnum.SENT, "m", false));
        when(repository.markResult(eq(1L), anyInt(), any(), any(), anyBoolean(), anyInt()))
                .thenThrow(new IllegalStateException("brok down"));

        job(true).run(new ScheduleInfoBean());

        verify(repository).markResult(eq(2L), eq(88), startsWith("200|m|"), eq(NOW), eq(true), eq(IN_PROGRESS));
    }

    @Test
    void e43_claimFailureIsLoggedAndRunEnds() {
        when(repository.claimPending(anyInt(), anyInt(), anyInt())).thenThrow(new IllegalStateException("brok down"));

        job(true).run(new ScheduleInfoBean());

        verifyNoInteractions(smsService);
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("SMS dispatch run failed"));
    }

    @Test
    void e42_messageIdIsTruncatedTo65Characters() {
        givenRows(row(1L, NUMBER));
        when(smsService.sendWithOutcome(any())).thenReturn(outcome(SmsRecipientResult.StatusEnum.SENT, "x".repeat(80), false));

        job(true).run(new ScheduleInfoBean());

        final ArgumentCaptor<String> messageId = ArgumentCaptor.forClass(String.class);
        verify(repository).markResult(anyLong(), anyInt(), messageId.capture(), any(), anyBoolean(), anyInt());
        assertThat(messageId.getValue()).hasSize(65).startsWith("200|xxx");
    }

    @Test
    void summaryAndRowLogsNeverContainBodyOrFullNumber() {
        givenRows(row(1L, NUMBER), row(2L, "12"));
        when(smsService.sendWithOutcome(any()))
                .thenReturn(outcome(SmsRecipientResult.StatusEnum.SENT, "m", false))
                .thenThrow(new SmsException("SMS002", "Invalid mobile number in: mobileNumbers[1]"));

        job(true).run(new ScheduleInfoBean());

        assertThat(logs.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain(NUMBER).doesNotContain("Statement ready"));
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage()
                .contains("claimed=2 sent=1 failed=0 invalid=1"));
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("***0010"));
    }
}
