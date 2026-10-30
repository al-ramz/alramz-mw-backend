package com.alramz.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alramz.config.SmsProperties;
import com.alramz.exception.SmsException;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.SmsRecipientResult;
import com.alramz.model.SmsSendRequest;
import com.alramz.model.SmsSendResult;
import com.alramz.service.SmsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsServiceImplTest {

    private static final String UAE = "971501234567";
    private static final String US = "14155550100";

    @Mock
    private EtisalatSmsClient etisalat;
    @Mock
    private WaselneySmsClient waselney;
    @Mock
    private VeriPhoneClient veriPhone;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        MDCUtil.putCorrelationId("cid-sms");
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(SmsServiceImpl.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(SmsServiceImpl.class)).detachAppender(logs);
        MDC.clear();
    }

    private SmsServiceImpl service(final boolean live, final String provider, final List<String> prefixes,
                                   final List<String> excludedCountries) {
        final SmsProperties props = new SmsProperties(live, provider, 50, 1500, Duration.ofSeconds(5), Duration.ofSeconds(10),
                new SmsProperties.Defaults("4.2", 300),
                new SmsProperties.Etisalat("https://e", "u", "p", prefixes, excludedCountries),
                new SmsProperties.Waselney("https://w", "https://w/r", "u", "p"),
                new SmsProperties.Veriphone("https://v", "k"), null);
        return new SmsServiceImpl(props, etisalat, waselney, veriPhone);
    }

    private SmsServiceImpl liveWaselney() {
        return service(true, "WASELNEY", List.of(), List.of());
    }

    private static SmsSendRequest request(final String... numbers) {
        return new SmsSendRequest().messageBody("Your statement is ready").mobileNumbers(new ArrayList<>(List.of(numbers)));
    }

    private static void assertResult(final SmsRecipientResult r, final String number,
                                     final SmsRecipientResult.ServiceProviderEnum provider,
                                     final SmsRecipientResult.StatusEnum status, final String messageId) {
        assertThat(r.getMobileNumber()).isEqualTo(number);
        assertThat(r.getServiceProvider()).isEqualTo(provider);
        assertThat(r.getStatus()).isEqualTo(status);
        assertThat(r.getMessageId()).isEqualTo(messageId);
    }

    private void assertFails(final SmsService service, final SmsSendRequest request, final String code, final String message) {
        assertThatThrownBy(() -> service.sendWithOutcome(request))
                .isInstanceOf(SmsException.class)
                .hasFieldOrPropertyWithValue("code", code)
                .hasMessage(message);
        verifyNoInteractions(etisalat, waselney, veriPhone);
    }

    // ---- main scenario ----

    @Test
    void liveWaselneySendsEveryNumberAndReturnsOneResultEach() {
        when(waselney.send(UAE, "Your statement is ready")).thenReturn("88123456");
        when(waselney.send(US, "Your statement is ready")).thenReturn("88123457");

        final SmsService.Outcome outcome = liveWaselney().sendWithOutcome(request(UAE, "+" + US));

        final SmsSendResult result = outcome.result();
        assertThat(result.getCorrelationId()).isEqualTo("cid-sms");
        assertThat(result.getSimulated()).isFalse();
        assertThat(result.getResults()).hasSize(2);
        assertResult(result.getResults().get(0), UAE, SmsRecipientResult.ServiceProviderEnum.WASELNEY,
                SmsRecipientResult.StatusEnum.SENT, "88123456");
        assertResult(result.getResults().get(1), US, SmsRecipientResult.ServiceProviderEnum.WASELNEY,
                SmsRecipientResult.StatusEnum.SENT, "88123457");
        assertThat(outcome.anyProviderRejected()).isFalse();
        verifyNoInteractions(etisalat, veriPhone);
    }

    @Test
    void sendDefaultMethodReturnsResult() {
        when(waselney.send(anyString(), anyString())).thenReturn("1");

        assertThat(liveWaselney().send(request(UAE)).getResults()).hasSize(1);
    }

    @Test
    void e26_simulatedModeMakesNoProviderCalls() {
        final SmsServiceImpl service = service(false, "ETISALAT", List.of("1"), List.of("US"));

        final SmsSendResult result = service.send(request(UAE, US));

        assertThat(result.getSimulated()).isTrue();
        assertResult(result.getResults().get(0), UAE, SmsRecipientResult.ServiceProviderEnum.ETISALAT,
                SmsRecipientResult.StatusEnum.SENT, UAE);
        assertResult(result.getResults().get(1), US, SmsRecipientResult.ServiceProviderEnum.ETISALAT,
                SmsRecipientResult.StatusEnum.SENT, US);
        verifyNoInteractions(etisalat, waselney, veriPhone);
    }

    @Test
    void e26_simulatedWaselneyUsesNumberAsMessageId() {
        final SmsSendResult result = service(false, "WASELNEY", List.of(), List.of()).send(request(UAE));

        assertResult(result.getResults().get(0), UAE, SmsRecipientResult.ServiceProviderEnum.WASELNEY,
                SmsRecipientResult.StatusEnum.SENT, UAE);
        verifyNoInteractions(etisalat, waselney, veriPhone);
    }

    @Test
    void e25_duplicatesAndSeparatorsAreNormalised() {
        when(waselney.send(UAE, "Your statement is ready")).thenReturn("m1");

        final SmsSendResult result = liveWaselney().send(request(UAE + "; " + UAE, "+" + UAE));

        assertThat(result.getResults()).extracting(SmsRecipientResult::getMobileNumber).containsExactly(UAE);
    }

    // ---- routing ----

    @Test
    void etisalatWithEmptyPrefixesSendsEverythingToEtisalat() {
        when(etisalat.send(UAE, "Your statement is ready", "4.2", 300, "cid-sms")).thenReturn("job-1");

        final SmsSendResult result = service(true, "ETISALAT", List.of(), List.of()).send(request(UAE));

        assertResult(result.getResults().get(0), UAE, SmsRecipientResult.ServiceProviderEnum.ETISALAT,
                SmsRecipientResult.StatusEnum.SENT, "job-1");
        verifyNoInteractions(waselney, veriPhone);
    }

    @Test
    void prefixMatchRoutesByVeriphoneCountryAndWaselneyGoesFirst() {
        final SmsServiceImpl service = service(true, "ETISALAT", List.of("1", "44"), List.of("US"));
        when(veriPhone.countryCode(US)).thenReturn(Optional.of("us"));
        when(veriPhone.countryCode("447700900123")).thenReturn(Optional.of("GB"));
        when(etisalat.send(anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn("job");
        when(waselney.send(anyString(), anyString())).thenReturn("w");

        final SmsSendResult result = service.send(request(UAE, US, "447700900123").msgCategory("1.5").expiryInSecs(60));

        assertThat(result.getResults()).extracting(SmsRecipientResult::getMobileNumber)
                .containsExactly(UAE, US, "447700900123");
        assertThat(result.getResults()).extracting(SmsRecipientResult::getServiceProvider).containsExactly(
                SmsRecipientResult.ServiceProviderEnum.ETISALAT, SmsRecipientResult.ServiceProviderEnum.WASELNEY,
                SmsRecipientResult.ServiceProviderEnum.ETISALAT);
        verify(veriPhone, never()).countryCode(UAE);
        final InOrder order = inOrder(waselney, etisalat);
        order.verify(waselney).send(US, "Your statement is ready");
        order.verify(etisalat).send(UAE, "Your statement is ready", "1.5", 60, "cid-sms");
        order.verify(etisalat).send("447700900123", "Your statement is ready", "1.5", 60, "cid-sms");
    }

    @Test
    void e32_veriphoneFailureRoutesToEtisalat() {
        final SmsServiceImpl service = service(true, "ETISALAT", List.of("1"), List.of("US"));
        when(veriPhone.countryCode(US)).thenReturn(Optional.empty());
        when(etisalat.send(anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn("job");

        final SmsSendResult result = service.send(request(US));

        assertThat(result.getResults().get(0).getServiceProvider()).isEqualTo(SmsRecipientResult.ServiceProviderEnum.ETISALAT);
        verifyNoInteractions(waselney);
    }

    // ---- provider failures ----

    @Test
    void e27_partialFailureKeepsPerNumberStatus() {
        when(waselney.send(UAE, "Your statement is ready")).thenReturn("m1");
        when(waselney.send(US, "Your statement is ready")).thenThrow(new SmsException("SMS010", "SMS provider unavailable"));

        final SmsService.Outcome outcome = liveWaselney().sendWithOutcome(request(UAE, US));

        assertThat(outcome.result().getResults()).extracting(SmsRecipientResult::getStatus).containsExactly(
                SmsRecipientResult.StatusEnum.SENT, SmsRecipientResult.StatusEnum.FAILED);
        assertThat(outcome.result().getResults().get(1).getMessageId()).isNull();
        assertThat(outcome.anyProviderRejected()).isFalse();
    }

    @Test
    void e28_anyRejectionIsReported() {
        when(waselney.send(UAE, "Your statement is ready")).thenThrow(new SmsException("SMS009", "rejected"));
        when(waselney.send(US, "Your statement is ready")).thenThrow(new SmsException("SMS010", "down"));

        assertThat(liveWaselney().sendWithOutcome(request(UAE, US)).anyProviderRejected()).isTrue();
    }

    // ---- validation (E20-E24) ----

    @Test
    void e20_missingBodyAndNumbers() {
        assertFails(liveWaselney(), new SmsSendRequest().messageBody(" "), "SMS001",
                "Request failed due to missing: messageBody, mobileNumbers");
        assertFails(liveWaselney(), request(" ; "), "SMS001", "Request failed due to missing: mobileNumbers");
        final List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertFails(liveWaselney(), request().mobileNumbers(withNull), "SMS001", "Request failed due to missing: mobileNumbers");
    }

    @Test
    void e21_invalidNumberReportsOneBasedEntryIndex() {
        assertFails(liveWaselney(), request(UAE, "97150123456x"), "SMS002", "Invalid mobile number in: mobileNumbers[2]");
        assertFails(liveWaselney(), request("0501234567"), "SMS002", "Invalid mobile number in: mobileNumbers[1]");
        assertFails(liveWaselney(), request("1234567"), "SMS002", "Invalid mobile number in: mobileNumbers[1]");
    }

    @Test
    void e22_bodyTooLong() {
        assertFails(liveWaselney(), request(UAE).messageBody("x".repeat(1501)), "SMS003", "Field length exceeded: messageBody");
    }

    @Test
    void e22_bodyAtLimitIsAccepted() {
        when(waselney.send(anyString(), anyString())).thenReturn("m");

        assertThat(liveWaselney().send(request(UAE).messageBody("x".repeat(1500))).getResults()).hasSize(1);
    }

    @Test
    void e23_tooManyRecipientsAfterSplitting() {
        final String many = String.join(",", IntStream.range(0, 51).mapToObj(i -> "9715000000" + (10 + i)).toList());

        assertFails(liveWaselney(), request(many), "SMS004", "Number of recipients exceeds 50");
    }

    @Test
    void e23_fiftyDistinctAfterDeduplicationIsAccepted() {
        when(waselney.send(anyString(), anyString())).thenReturn("m");
        final List<String> numbers = new ArrayList<>(IntStream.range(0, 50).mapToObj(i -> "9715000000" + (10 + i)).toList());
        numbers.addAll(Collections.nCopies(5, "971500000010"));

        assertThat(liveWaselney().send(request(numbers.toArray(String[]::new))).getResults()).hasSize(50);
    }

    @Test
    void e24_invalidOptionalFields() {
        assertFails(liveWaselney(), request(UAE).msgCategory("abc"), "SMS005", "Invalid field: msgCategory");
        assertFails(liveWaselney(), request(UAE).expiryInSecs(0), "SMS005", "Invalid field: expiryInSecs");
        assertFails(liveWaselney(), request(UAE).expiryInSecs(86_401), "SMS005", "Invalid field: expiryInSecs");
    }

    // ---- logging hygiene ----

    @Test
    void logsMaskNumbersAndNeverContainTheBody() {
        when(waselney.send(anyString(), anyString())).thenThrow(new SmsException("SMS009", "rejected"));

        liveWaselney().send(request(UAE));

        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain(UAE).doesNotContain("Your statement is ready"));
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("***4567"));
    }

    @Test
    void maskKeepsLastFourDigits() {
        assertThat(SmsServiceImpl.mask(UAE)).isEqualTo("***4567");
        assertThat(SmsServiceImpl.mask("123")).isEqualTo("***");
        assertThat(SmsServiceImpl.mask(null)).isEqualTo("***");
    }
}
