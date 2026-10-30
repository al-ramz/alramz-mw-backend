package com.alramz.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alramz.config.EmailProperties;
import com.alramz.config.OtpProperties;
import com.alramz.exception.EmailServiceException;
import com.alramz.exception.EmailValidationException;
import com.alramz.exception.OtpException;
import com.alramz.exception.SmsException;
import com.alramz.jwt.context.JwtContext;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.EmailRequest;
import com.alramz.model.OtpChannelResult;
import com.alramz.model.OtpConfiguration;
import com.alramz.model.OtpConfigurationList;
import com.alramz.model.OtpSendRequest;
import com.alramz.model.OtpSendResult;
import com.alramz.model.OtpValidateRequest;
import com.alramz.model.OtpValidateResult;
import com.alramz.model.SmsRecipientResult;
import com.alramz.model.SmsSendRequest;
import com.alramz.model.SmsSendResult;
import com.alramz.repository.OtpRepository;
import com.alramz.repository.OtpRepository.OtpCandidate;
import com.alramz.repository.OtpRepository.OtpConfig;
import com.alramz.repository.OtpRepository.OtpRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    // 21:30 UTC on the 26th is 01:30 on the 27th in Dubai: the daily window must follow Dubai, not UTC.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T21:30:00Z"), DUBAI);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 1, 30);
    private static final LocalDateTime DAY_START = LocalDateTime.of(2026, 10, 27, 0, 0);
    private static final EmailProperties EMAIL_PROPERTIES =
            new EmailProperties(255, 10_000, 10, 3_000_000, List.of("notification@alramz.ae"));
    private static final String MOBILE = "971500000010";
    private static final String EMAIL = "USER@EXAMPLE.COM";
    private static final String HASH_1234 = TemplatedEmailService.sha256Hex("1234");

    @Mock
    private OtpRepository repository;
    @Mock
    private SmsService smsService;
    @Mock
    private EmailService emailService;
    @Mock
    private PlatformTransactionManager txManager;
    @Mock
    private ObjectProvider<JwtContext> jwtContextProvider;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        MDCUtil.putCorrelationId("cid-otp");
        lenient().when(txManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(OtpService.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(OtpService.class)).detachAppender(logs);
        MDC.clear();
    }

    private OtpService service(final boolean fixedTestOtp) {
        return new OtpService(repository, smsService, emailService, new OtpProperties(fixedTestOtp, "notification@alramz.ae"),
                EMAIL_PROPERTIES, txManager, new MockEnvironment(), jwtContextProvider, CLOCK);
    }

    private static OtpConfig config(final String name, final String sentVia, final Integer maxValidation) {
        return new OtpConfig(1, name, 300, 6, "Your OTP is %OTP%", "رمز %OTP%", 5, sentVia,
                "Your OTP ^+^ <p>Code %OTP% (%OTP%)</p>", "ع ^+^ ب %OTP%", maxValidation);
    }

    private void givenConfig(final OtpConfig config) {
        when(repository.findConfig(config.serviceName())).thenReturn(Optional.of(config));
    }

    private static OtpSendRequest sendRequest(final String recipients, final String language) {
        return new OtpSendRequest().serviceName("LOGIN_OTP").recipients(recipients).language(language).ipAddress("10.0.0.1");
    }

    private static OtpValidateRequest validateRequest(final String hash) {
        return new OtpValidateRequest().serviceName("LOGIN_OTP").recipients(MOBILE).hashedOtp(hash);
    }

    private static SmsService.Outcome smsOutcome(final SmsRecipientResult.StatusEnum status) {
        return new SmsService.Outcome(new SmsSendResult().correlationId("c").simulated(false).results(List.of(
                new SmsRecipientResult().mobileNumber(MOBILE).serviceProvider(SmsRecipientResult.ServiceProviderEnum.WASELNEY)
                        .status(status))), false);
    }

    private static OtpChannelResult channel(final OtpChannelResult.ChannelEnum channel,
                                            final OtpChannelResult.StatusEnum status) {
        return new OtpChannelResult().channel(channel).status(status);
    }

    private void assertNoWritesOrSends() {
        verify(repository, never()).insertOtp(any());
        verify(repository, never()).deactivateActive(anyString(), anyList());
        verifyNoInteractions(smsService, emailService);
    }

    private static void assertCode(final Runnable call, final String code, final String message) {
        assertThatThrownBy(call::run).isInstanceOf(OtpException.class)
                .hasFieldOrPropertyWithValue("code", code).hasMessage(message);
    }

    // ---------- send ----------

    @Test
    void e10_partialDeliveryIs200WithPerChannelStatusAndTransactionsAroundTheSend() {
        givenConfig(config("LOGIN_OTP", "EMAIL|SMS", null));
        when(repository.insertOtp(any())).thenReturn(10L, 11L);
        when(smsService.sendWithOutcome(any())).thenReturn(smsOutcome(SmsRecipientResult.StatusEnum.SENT));
        doThrow(new EmailServiceException("MGE-B-003", "graph down")).when(emailService).sendEmail(any());

        final OtpSendResult result = service(false).send(sendRequest("user@example.com|+971500000010", "EN"));

        assertThat(result.getCorrelationId()).isEqualTo("cid-otp");
        assertThat(result.getChannels()).containsExactly(
                channel(OtpChannelResult.ChannelEnum.SMS, OtpChannelResult.StatusEnum.SENT),
                channel(OtpChannelResult.ChannelEnum.EMAIL, OtpChannelResult.StatusEnum.FAILED));

        final ArgumentCaptor<SmsSendRequest> sms = ArgumentCaptor.forClass(SmsSendRequest.class);
        final ArgumentCaptor<OtpRow> rows = ArgumentCaptor.forClass(OtpRow.class);
        final InOrder order = inOrder(repository, txManager, smsService, emailService);
        order.verify(repository).deactivateActive("LOGIN_OTP", List.of(MOBILE, EMAIL));
        order.verify(repository, times(2)).insertOtp(rows.capture());
        order.verify(txManager).commit(any());                       // tx1 committed before any delivery
        order.verify(smsService).sendWithOutcome(sms.capture());
        order.verify(emailService).sendEmail(any());
        order.verify(repository).markSent(List.of(10L));
        order.verify(repository).markFailed(List.of(11L));
        order.verify(repository).upsertAttempt("LOGIN_OTP", MOBILE, NOW, DAY_START);
        order.verify(repository).upsertAttempt("LOGIN_OTP", EMAIL, NOW, DAY_START);
        order.verify(txManager).commit(any());                       // tx2
        verify(txManager, times(2)).getTransaction(any());

        assertThat(sms.getValue().getMsgCategory()).isEqualTo("4.5");
        assertThat(sms.getValue().getExpiryInSecs()).isEqualTo(300);
        assertThat(sms.getValue().getMobileNumbers()).containsExactly(MOBILE);
        final String otp = sms.getValue().getMessageBody().substring("Your OTP is ".length());
        assertThat(otp).matches("^[0-9]{6}$");
        assertThat(rows.getAllValues()).extracting(OtpRow::mobileEmail).containsExactly(MOBILE, EMAIL);
        assertThat(rows.getAllValues()).allSatisfy(row -> {
            assertThat(row.otpHash()).isEqualTo(TemplatedEmailService.sha256Hex(otp)).isEqualTo(row.otpHash().toLowerCase(Locale.ROOT));
            assertThat(row.created()).isEqualTo(NOW);
            assertThat(row.expiry()).isEqualTo(NOW.plusSeconds(300));
            assertThat(row.ipAddress()).isEqualTo("10.0.0.1");
        });
    }

    @Test
    void emailIsSentOncePerAddressWithSubjectBodySplitAndIgnoresMobilesForAnEmailOnlyService() {
        givenConfig(config("LOGIN_OTP", "EMAIL", null));
        when(repository.insertOtp(any())).thenReturn(10L, 11L);

        final OtpSendResult result = service(false).send(sendRequest("a@example.com; b@example.com, 971500000011", "AR"));

        final ArgumentCaptor<EmailRequest> emails = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService, times(2)).sendEmail(emails.capture());
        assertThat(emails.getAllValues()).extracting(EmailRequest::getTo).containsExactly("A@EXAMPLE.COM", "B@EXAMPLE.COM");
        assertThat(emails.getAllValues()).allSatisfy(email -> {
            assertThat(email.getFrom()).isEqualTo("notification@alramz.ae");
            assertThat(email.getSubject()).isEqualTo("ع");
            assertThat(email.getBody()).matches("^ب [0-9]{6}$");
        });
        verifyNoInteractions(smsService);
        verify(repository).deactivateActive("LOGIN_OTP", List.of("A@EXAMPLE.COM", "B@EXAMPLE.COM"));
        verify(repository).markSent(List.of(10L, 11L));
        verify(repository, never()).markFailed(anyList());
        assertThat(result.getChannels()).containsExactly(
                channel(OtpChannelResult.ChannelEnum.EMAIL, OtpChannelResult.StatusEnum.SENT));
    }

    @Test
    void englishEmailReplacesEveryOtpPlaceholder() {
        givenConfig(config("LOGIN_OTP", "EMAIL", null));
        when(repository.insertOtp(any())).thenReturn(10L);

        service(false).send(sendRequest(EMAIL, "EN"));

        final ArgumentCaptor<EmailRequest> email = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendEmail(email.capture());
        assertThat(email.getValue().getSubject()).isEqualTo("Your OTP");
        assertThat(email.getValue().getBody()).matches("^<p>Code ([0-9]{6}) \\(\\1\\)</p>$");
    }

    @Test
    void e9_fixedTestOtpStoresFixedHashWithoutAnyChannelCall() {
        givenConfig(config("LOGIN_OTP", "EMAIL|SMS", null));
        when(repository.insertOtp(any())).thenReturn(10L, 11L);

        final OtpSendResult result = service(true).send(sendRequest(MOBILE + " " + EMAIL, "EN"));

        verifyNoInteractions(smsService, emailService);
        final ArgumentCaptor<OtpRow> rows = ArgumentCaptor.forClass(OtpRow.class);
        verify(repository, times(2)).insertOtp(rows.capture());
        assertThat(rows.getAllValues()).extracting(OtpRow::otpHash).containsOnly(HASH_1234);
        verify(repository).markSent(List.of(10L, 11L));
        assertThat(result.getChannels()).extracting(OtpChannelResult::getStatus)
                .containsExactly(OtpChannelResult.StatusEnum.SENT, OtpChannelResult.StatusEnum.SENT);
    }

    @Test
    void e9_fixedTestOtpIsSixDigitsForServiceNamesEndingInUnderscore() {
        givenConfig(config("ALPHA_", "SMS", null));
        when(repository.insertOtp(any())).thenReturn(10L);

        service(true).send(sendRequest(MOBILE, "EN").serviceName("ALPHA_"));

        final ArgumentCaptor<OtpRow> row = ArgumentCaptor.forClass(OtpRow.class);
        verify(repository).insertOtp(row.capture());
        assertThat(row.getValue().otpHash()).isEqualTo(TemplatedEmailService.sha256Hex("123456"));
    }

    @Test
    void e11_everyRecipientFailedIsOtp009AfterTheFailedRowsCommit() {
        givenConfig(config("LOGIN_OTP", "SMS", null));
        when(repository.insertOtp(any())).thenReturn(10L);
        when(smsService.sendWithOutcome(any())).thenReturn(smsOutcome(SmsRecipientResult.StatusEnum.FAILED));

        assertCode(() -> service(false).send(sendRequest(MOBILE, "AR")), "OTP009", "OTP delivery failed for every recipient");

        verify(repository).markFailed(List.of(10L));
        verify(repository, never()).markSent(anyList());
        verify(repository, never()).upsertAttempt(anyString(), anyString(), any(), any());
        verify(txManager, times(2)).commit(any());
    }

    @Test
    void e11_channelExceptionsMarkRecipientsFailed() {
        givenConfig(config("LOGIN_OTP", "EMAIL|SMS", null));
        when(repository.insertOtp(any())).thenReturn(10L, 11L);
        when(smsService.sendWithOutcome(any())).thenThrow(new SmsException("SMS004", "too many"));
        doThrow(new EmailValidationException("MGE-V-007", "sender")).when(emailService).sendEmail(any());

        assertCode(() -> service(false).send(sendRequest(MOBILE + "|" + EMAIL, "EN")), "OTP009",
                "OTP delivery failed for every recipient");

        verify(repository).markFailed(List.of(10L, 11L));
        assertThat(messages()).anyMatch(m -> m.contains("OTP email misconfiguration MGE-V-007"))
                .anyMatch(m -> m.contains("OTP SMS failed code=SMS004"));
    }

    @Test
    void e11_unexpectedChannelExceptionsAreLoggedByClassName() {
        givenConfig(config("LOGIN_OTP", "EMAIL|SMS", null));
        when(repository.insertOtp(any())).thenReturn(10L, 11L);
        when(smsService.sendWithOutcome(any())).thenThrow(new IllegalStateException("http://secret"));
        doThrow(new IllegalArgumentException("x")).when(emailService).sendEmail(any());

        assertCode(() -> service(false).send(sendRequest(MOBILE + "|" + EMAIL, "EN")), "OTP009",
                "OTP delivery failed for every recipient");

        assertThat(messages()).anyMatch(m -> m.contains("code=IllegalStateException"))
                .anyMatch(m -> m.contains("code=IllegalArgumentException"))
                .noneMatch(m -> m.contains("secret"));
    }

    @Test
    void finalizeFailureAfterDeliveryIsLoggedAndStillReturns200() {
        givenConfig(config("LOGIN_OTP", "SMS", null));
        when(repository.insertOtp(any())).thenReturn(10L);
        when(smsService.sendWithOutcome(any())).thenReturn(smsOutcome(SmsRecipientResult.StatusEnum.SENT));
        doThrow(new DataAccessResourceFailureException("db down")).when(repository).markSent(anyList());

        final OtpSendResult result = service(false).send(sendRequest(MOBILE, "EN"));

        assertThat(result.getChannels()).containsExactly(
                channel(OtpChannelResult.ChannelEnum.SMS, OtpChannelResult.StatusEnum.SENT));
        verify(txManager).rollback(any());
        assertThat(messages()).anyMatch(m -> m.contains("OTP record failed") && m.contains("DataAccessResourceFailureException"));
    }

    @Test
    void e1_missingOrInvalidFieldsAreCollectedInOrder() {
        assertCode(() -> service(false).send(new OtpSendRequest()), "OTP001",
                "Request failed due to missing: serviceName, recipients, language, ipAddress");
        assertCode(() -> service(false).send(new OtpSendRequest().serviceName(" ").recipients(" | ; ")
                        .language("en").ipAddress("10.0.0.1; DROP")), "OTP001",
                "Request failed due to missing: serviceName, recipients, language, ipAddress");
        assertCode(() -> service(false).send(sendRequest(MOBILE, "FR")), "OTP001", "Request failed due to missing: language");
        verifyNoInteractions(repository, smsService, emailService);
    }

    @Test
    void ipv6AddressIsAccepted() {
        givenConfig(config("LOGIN_OTP", "SMS", null));
        when(repository.insertOtp(any())).thenReturn(10L);

        service(true).send(sendRequest(MOBILE, "EN").ipAddress("::ffff:10.0.0.1"));

        final ArgumentCaptor<OtpRow> row = ArgumentCaptor.forClass(OtpRow.class);
        verify(repository).insertOtp(row.capture());
        assertThat(row.getValue().ipAddress()).isEqualTo("::ffff:10.0.0.1");
    }

    @Test
    void e3_unknownServiceIsOtp002() {
        when(repository.findConfig("LOGIN_OTP")).thenReturn(Optional.empty());

        assertCode(() -> service(false).send(sendRequest(MOBILE, "EN")), "OTP002", "Invalid OTP service");
        assertNoWritesOrSends();
    }

    @Test
    void e4_tokensThatAreNeitherEmailNorMobileAreRejected() {
        givenConfig(config("LOGIN_OTP", "EMAIL|SMS", null));

        assertCode(() -> service(false).send(sendRequest(MOBILE + "|bad@", "EN")), "OTP004", "Invalid email");
        assertCode(() -> service(false).send(sendRequest(EMAIL + "|12ab", "EN")), "OTP003", "Invalid mobile number");
        assertNoWritesOrSends();
    }

    @Test
    void e5_noRecipientForTheConfiguredChannel() {
        givenConfig(config("LOGIN_OTP", "SMS", null));
        assertCode(() -> service(false).send(sendRequest(EMAIL, "EN")), "OTP003", "Invalid mobile number");

        givenConfig(config("LOGIN_OTP", "EMAIL", null));
        assertCode(() -> service(false).send(sendRequest(MOBILE, "EN")), "OTP004", "Invalid email");
        assertNoWritesOrSends();
    }

    @Test
    void e6_moreThanFiveKeptRecipientsIsOtp005AndDuplicatesCountOnce() {
        givenConfig(config("LOGIN_OTP", "SMS", null));

        assertCode(() -> service(false).send(sendRequest(
                        "971500000011,971500000012,971500000013,971500000014,971500000015,971500000016", "EN")),
                "OTP005", "Number of recipients exceeds 5");
        assertNoWritesOrSends();

        when(repository.insertOtp(any())).thenReturn(1L, 2L, 3L, 4L, 5L);
        service(true).send(sendRequest(
                "971500000011,+971500000011,971500000012,971500000013,971500000014,971500000015", "EN"));
        verify(repository, times(5)).insertOtp(any());
    }

    @Test
    void e7_dailyLimitUsesTheDubaiDayAndBlocksAtMaxAttempts() {
        givenConfig(config("LOGIN_OTP", "SMS", null));
        when(repository.maxAttemptsSince("LOGIN_OTP", List.of(MOBILE), DAY_START)).thenReturn(5);

        assertCode(() -> service(false).send(sendRequest(MOBILE, "EN")), "OTP006", "User exceeded maximum number of attempts");
        assertNoWritesOrSends();
    }

    @Test
    void e8_templateMisconfigurationIsOtp010WithoutSendOrRows() {
        final OtpConfig[] broken = {
            new OtpConfig(1, "LOGIN_OTP", 300, 6, " ", "x", 5, "SMS", null, null, null),
            new OtpConfig(1, "LOGIN_OTP", 300, 6, null, "x", 5, "SMS", null, null, null),
            new OtpConfig(1, "LOGIN_OTP", 300, 6, null, null, 5, "EMAIL", "no delimiter", null, null),
            new OtpConfig(1, "LOGIN_OTP", 300, 6, null, null, 5, "EMAIL", "  ^+^ body", null, null),
            new OtpConfig(1, "LOGIN_OTP", 300, 6, null, null, 5, "EMAIL", "subject ^+^  ", null, null),
            new OtpConfig(1, "LOGIN_OTP", 300, 6, null, null, 5, "EMAIL", null, null, null),
        };
        for (OtpConfig config : broken) {
            givenConfig(config);
            assertThatThrownBy(() -> service(false).send(sendRequest(MOBILE + "|" + EMAIL, "EN")))
                    .isInstanceOf(OtpException.class).hasFieldOrPropertyWithValue("code", "OTP010");
        }
        assertNoWritesOrSends();
    }

    @Test
    void unknownSentViaIsOtp010() {
        givenConfig(config("LOGIN_OTP", "WHATSAPP", null));

        assertThatThrownBy(() -> service(false).send(sendRequest(MOBILE, "EN")))
                .isInstanceOf(OtpException.class).hasFieldOrPropertyWithValue("code", "OTP010");
        assertNoWritesOrSends();
    }

    // ---------- validate ----------

    @Test
    void e17_e18_upperCaseHashMatchesAndOnlyMatchedIdsAreValidated() {
        givenConfig(config("LOGIN_OTP", "SMS", null));
        when(repository.lockActiveCandidates("LOGIN_OTP", List.of(MOBILE), NOW)).thenReturn(List.of(
                new OtpCandidate(21, MOBILE, "b".repeat(64), 1), new OtpCandidate(20, MOBILE, HASH_1234, 0)));

        final OtpValidateResult result = service(false).validate(validateRequest(HASH_1234.toUpperCase(Locale.ROOT)));

        assertThat(result.getValid()).isTrue();
        assertThat(result.getCorrelationId()).isEqualTo("cid-otp");
        verify(repository).markValidated(List.of(20L));
        verify(repository).resetAttempts("LOGIN_OTP", List.of(MOBILE));
        verify(repository, never()).incrementValidationAttempts(anyList());
        verify(txManager).commit(any());
    }

    @Test
    void matchResetsTheDailyCounterOnlyForTheMatchedRecipient() {
        // Security regression: validating your own OTP with a victim listed alongside must not clear the victim's cap.
        givenConfig(config("LOGIN_OTP", "EMAIL|SMS", null));
        when(repository.lockActiveCandidates("LOGIN_OTP", List.of(MOBILE, EMAIL), NOW)).thenReturn(List.of(
                new OtpCandidate(31, EMAIL, HASH_1234, 0), new OtpCandidate(30, MOBILE, "b".repeat(64), 0)));

        assertThat(service(false).validate(new OtpValidateRequest().serviceName("LOGIN_OTP")
                .recipients(MOBILE + "|" + EMAIL).hashedOtp(HASH_1234)).getValid()).isTrue();

        verify(repository).markValidated(List.of(31L));
        verify(repository).resetAttempts("LOGIN_OTP", List.of(EMAIL));
    }

    @Test
    void e16_wrongHashIncrementsEveryCandidateAndThrowsOtp007AfterCommit() {
        givenConfig(config("LOGIN_OTP", "SMS", null));
        when(repository.lockActiveCandidates("LOGIN_OTP", List.of(MOBILE), NOW)).thenReturn(List.of(
                new OtpCandidate(21, MOBILE, "b".repeat(64), 2), new OtpCandidate(20, MOBILE, "c".repeat(64), 0)));

        assertCode(() -> service(false).validate(validateRequest(HASH_1234)), "OTP007", "Invalid OTP");

        final InOrder order = inOrder(repository, txManager);
        order.verify(repository).incrementValidationAttempts(List.of(21L, 20L));
        order.verify(txManager).commit(any());
        verify(txManager, never()).rollback(any());
        verify(repository, never()).markValidated(anyList());
    }

    @Test
    void e14_noActiveCandidateIsOtp007WithoutWrites() {
        givenConfig(config("LOGIN_OTP", "SMS", null));
        when(repository.lockActiveCandidates("LOGIN_OTP", List.of(MOBILE), NOW)).thenReturn(List.of());

        assertCode(() -> service(false).validate(validateRequest(HASH_1234)), "OTP007", "Invalid OTP");

        verify(repository, never()).incrementValidationAttempts(anyList());
        verify(txManager).commit(any());
    }

    @Test
    void e15_nullLimitDefaultsToThreeAndBlocksWithRollbackAndNoWrites() {
        givenConfig(config("LOGIN_OTP", "SMS", null));
        when(repository.lockActiveCandidates("LOGIN_OTP", List.of(MOBILE), NOW))
                .thenReturn(List.of(new OtpCandidate(20, MOBILE, HASH_1234, 3)));

        assertCode(() -> service(false).validate(validateRequest(HASH_1234)), "OTP008",
                "User exceeded maximum number of validation attempts");

        verify(txManager).rollback(any());
        verify(repository, never()).markValidated(anyList());
        verify(repository, never()).incrementValidationAttempts(anyList());
    }

    @Test
    void configuredLimitAboveThreeAllowsAFourthAttempt() {
        givenConfig(config("LOGIN_OTP", "SMS", 5));
        when(repository.lockActiveCandidates("LOGIN_OTP", List.of(MOBILE), NOW))
                .thenReturn(List.of(new OtpCandidate(20, MOBILE, HASH_1234, 3)));

        assertThat(service(false).validate(validateRequest(HASH_1234)).getValid()).isTrue();
    }

    @Test
    void e2_missingOrMalformedHashIsOtp001WithoutTouchingTheDatabase() {
        assertCode(() -> service(false).validate(new OtpValidateRequest()), "OTP001",
                "Request failed due to missing: serviceName, recipients, hashedOtp");
        assertCode(() -> service(false).validate(validateRequest("xyz")), "OTP001",
                "Request failed due to missing: hashedOtp");
        assertCode(() -> service(false).validate(validateRequest("g".repeat(64))), "OTP001",
                "Request failed due to missing: hashedOtp");
        verifyNoInteractions(repository, txManager);
    }

    @Test
    void e3_validateUnknownServiceIsOtp002Not500() {
        when(repository.findConfig("LOGIN_OTP")).thenReturn(Optional.empty());

        assertCode(() -> service(false).validate(validateRequest(HASH_1234)), "OTP002", "Invalid OTP service");
        verifyNoInteractions(txManager);
    }

    // ---------- configurations ----------

    @Test
    void e24_configurationsMapEveryRowAndAnEmptyTableIsAnEmptyList() {
        when(repository.findAllConfigs()).thenReturn(List.of(config("LOGIN_OTP", "EMAIL|SMS", 3)));

        final OtpConfigurationList list = service(false).configurations();

        assertThat(list.getCorrelationId()).isEqualTo("cid-otp");
        assertThat(list.getMessages()).containsExactly(new OtpConfiguration().serviceName("LOGIN_OTP").expiryInSeconds(300)
                .tokenLength(6).maxAttempts(5).smsMessageEnglish("Your OTP is %OTP%").smsMessageArabic("رمز %OTP%")
                .emailMessageEnglish("Your OTP ^+^ <p>Code %OTP% (%OTP%)</p>").emailMessageArabic("ع ^+^ ب %OTP%")
                .sentVia(OtpConfiguration.SentViaEnum.EMAIL_SMS));

        when(repository.findAllConfigs()).thenReturn(List.of());
        assertThat(service(false).configurations().getMessages()).isEmpty();
    }

    // ---------- startup guards (E23) ----------

    @Test
    void e23_fixedTestOtpInProdFailsStartup() {
        final MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new OtpService(repository, smsService, emailService,
                new OtpProperties(true, "notification@alramz.ae"), EMAIL_PROPERTIES, txManager, prod, jwtContextProvider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.otp.fixed-test-otp-enabled must be false in prod and preprod");
        final MockEnvironment preprod = new MockEnvironment();
        preprod.setActiveProfiles("preprod");
        assertThatThrownBy(() -> new OtpService(repository, smsService, emailService,
                new OtpProperties(true, "notification@alramz.ae"), EMAIL_PROPERTIES, txManager, preprod, jwtContextProvider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.otp.fixed-test-otp-enabled must be false in prod and preprod");
        // prod with the flag off, and dev with it on, both start.
        new OtpService(repository, smsService, emailService, new OtpProperties(false, "notification@alramz.ae"),
                EMAIL_PROPERTIES, txManager, prod, jwtContextProvider);
        final MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        new OtpService(repository, smsService, emailService, new OtpProperties(true, "notification@alramz.ae"),
                EMAIL_PROPERTIES, txManager, dev, jwtContextProvider);
    }

    @Test
    void e23_emailFromOutsideTheAllowListFailsStartup() {
        for (String from : new String[]{"cm@alramz.ae", null}) {
            assertThatThrownBy(() -> new OtpService(repository, smsService, emailService, new OtpProperties(false, from),
                    EMAIL_PROPERTIES, txManager, new MockEnvironment(), jwtContextProvider, CLOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("app.otp.email-from is not an allowed sender");
        }
        // Case-insensitive and trimmed.
        new OtpService(repository, smsService, emailService, new OtpProperties(false, " Notification@Alramz.AE "),
                EMAIL_PROPERTIES, txManager, new MockEnvironment(), jwtContextProvider, CLOCK);
    }

    // ---------- logging ----------

    @Test
    void logsNeverCarryTheOtpHashAddressesTextOrNumbers() {
        givenConfig(config("LOGIN_OTP", "EMAIL|SMS", null));
        when(repository.insertOtp(any())).thenReturn(10L, 11L);
        when(smsService.sendWithOutcome(any())).thenReturn(smsOutcome(SmsRecipientResult.StatusEnum.SENT));
        when(repository.lockActiveCandidates(anyString(), anyList(), any())).thenReturn(List.of());

        service(true).send(sendRequest(MOBILE + "|" + EMAIL, "EN"));
        service(false).send(sendRequest(MOBILE + "|" + EMAIL, "EN"));
        assertThatThrownBy(() -> service(false).validate(validateRequest(HASH_1234))).isInstanceOf(OtpException.class);

        final ArgumentCaptor<SmsSendRequest> sms = ArgumentCaptor.forClass(SmsSendRequest.class);
        verify(smsService).sendWithOutcome(sms.capture());
        final String otp = sms.getValue().getMessageBody().substring("Your OTP is ".length());
        assertThat(messages()).isNotEmpty().allSatisfy(m -> assertThat(m.toUpperCase(Locale.ROOT))
                .doesNotContain(otp, "1234", HASH_1234.toUpperCase(Locale.ROOT), "EXAMPLE.COM", "YOUR OTP", MOBILE));
        assertThat(messages()).anyMatch(m -> m.contains("OTP send outcome=OK") && m.contains("fixedTestOtp=true"))
                .anyMatch(m -> m.contains("OTP validate outcome=OTP007"));
    }

    private List<String> messages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
