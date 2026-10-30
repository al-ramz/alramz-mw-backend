package com.alramz.controllers;

import com.alramz.config.SmsProperties;
import com.alramz.exception.SmsException;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import com.alramz.model.SmsRecipientResult;
import com.alramz.model.SmsReportResult;
import com.alramz.model.SmsSendRequest;
import com.alramz.model.SmsSendResult;
import com.alramz.service.SmsService;
import com.alramz.service.impl.WaselneySmsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsControllerTest {

    @Mock
    private SmsService smsService;
    @Mock
    private WaselneySmsClient waselney;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private SmsController controller(final boolean live) {
        return new SmsController(smsService, waselney,
                new SmsProperties(live, "WASELNEY", 50, 1500, null, null, null, null,
                        new SmsProperties.Waselney("https://w", "https://w/r", "u", "p"), null, null));
    }

    private static SmsRecipientResult result(final SmsRecipientResult.StatusEnum status) {
        return new SmsRecipientResult().mobileNumber("971500000010")
                .serviceProvider(SmsRecipientResult.ServiceProviderEnum.WASELNEY).status(status);
    }

    private static SmsService.Outcome outcome(final boolean rejected, final SmsRecipientResult... results) {
        return new SmsService.Outcome(new SmsSendResult().correlationId("cid-9").simulated(false).results(List.of(results)),
                rejected);
    }

    @Test
    void e27_partialSuccessReturns200WithResults() {
        final SmsSendRequest request = new SmsSendRequest().messageBody("m");
        final SmsService.Outcome outcome = outcome(true, result(SmsRecipientResult.StatusEnum.SENT),
                result(SmsRecipientResult.StatusEnum.FAILED));
        when(smsService.sendWithOutcome(request)).thenReturn(outcome);

        final ResponseEntity<GenericResponse> response = controller(true).sendSms(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getResponseCode()).isEqualTo("200");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("OK");
        assertThat(response.getBody().getResponse()).isSameAs(outcome.result());
    }

    @Test
    void e28_allFailedWithRejectionIsSms009() {
        final SmsSendRequest request = new SmsSendRequest();
        when(smsService.sendWithOutcome(request)).thenReturn(outcome(true, result(SmsRecipientResult.StatusEnum.FAILED)));

        assertThatThrownBy(() -> controller(true).sendSms(request))
                .isInstanceOf(SmsException.class).hasFieldOrPropertyWithValue("code", "SMS009");
    }

    @Test
    void e29_allFailedByTransportIsSms010() {
        final SmsSendRequest request = new SmsSendRequest();
        when(smsService.sendWithOutcome(request)).thenReturn(outcome(false, result(SmsRecipientResult.StatusEnum.FAILED)));

        assertThatThrownBy(() -> controller(true).sendSms(request))
                .isInstanceOf(SmsException.class).hasFieldOrPropertyWithValue("code", "SMS010");
    }

    @Test
    void reportReturnsProviderReports() {
        MDCUtil.putCorrelationId("cid-r");
        final List<Map<String, String>> reports = List.of(Map.of("Status", "DELIVERED"));
        when(waselney.report("88-12ab")).thenReturn(reports);

        final ResponseEntity<GenericResponse> response = controller(true).getSmsReport("88-12ab");

        final SmsReportResult body = (SmsReportResult) response.getBody().getResponse();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body.getCorrelationId()).isEqualTo("cid-r");
        assertThat(body.getMessageId()).isEqualTo("88-12ab");
        assertThat(body.getReports()).isEqualTo(reports);
    }

    @Test
    void reportGeneratesCorrelationIdWhenMdcIsEmpty() {
        when(waselney.report("1")).thenReturn(List.of(Map.of("a", "b")));

        assertThat(((SmsReportResult) controller(true).getSmsReport("1").getBody().getResponse()).getCorrelationId())
                .hasSize(36);
    }

    @Test
    void e33_invalidMessageIdIsSms007WithoutProviderCall() {
        for (String bad : new String[]{"abc&pwd=x", "", "a".repeat(66), "a b"}) {
            assertThatThrownBy(() -> controller(true).getSmsReport(bad))
                    .isInstanceOf(SmsException.class)
                    .hasFieldOrPropertyWithValue("code", "SMS007")
                    .hasMessage("Invalid message id");
        }
        assertThatThrownBy(() -> controller(true).getSmsReport(null)).hasFieldOrPropertyWithValue("code", "SMS007");
        verifyNoInteractions(waselney);
    }

    @Test
    void e34_simulatedModeReportIsNotFoundWithoutProviderCall() {
        assertThatThrownBy(() -> controller(false).getSmsReport("88123456"))
                .isInstanceOf(SmsException.class)
                .hasFieldOrPropertyWithValue("code", "SMS008")
                .hasMessage("No SMS record found");
        verifyNoInteractions(waselney);
    }

    @Test
    void requestBindsLegacyAliasesIncludingSingleStringNumber() {
        final tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();

        assertThat(mapper.readValue("{\"messageBody\":\"m\",\"mobileNumber\":\"971500000010\"}", SmsSendRequest.class)
                .getMobileNumbers()).containsExactly("971500000010");
        assertThat(mapper.readValue("{\"mobileNumbers\":[\"1\",\"2\"]}", SmsSendRequest.class)
                .getMobileNumbers()).containsExactly("1", "2");
    }

    @Test
    void bothOperationsRequireAppNotificationRole() throws NoSuchMethodException {
        for (Method method : new Method[]{SmsController.class.getMethod("sendSms", SmsSendRequest.class),
                SmsController.class.getMethod("getSmsReport", String.class)}) {
            final JwtSecured secured = method.getAnnotation(JwtSecured.class);
            assertThat(secured).isNotNull();
            assertThat(secured.roles()).containsExactly("APP_NOTIFICATION");
        }
    }
}
