package com.alramz.controllers;

import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.model.GenericResponse;
import com.alramz.model.OtpConfigurationList;
import com.alramz.model.OtpSendRequest;
import com.alramz.model.OtpSendResult;
import com.alramz.model.OtpValidateRequest;
import com.alramz.model.OtpValidateResult;
import com.alramz.service.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpControllerTest {

    @Mock
    private OtpService otpService;

    private static void assertOk(final ResponseEntity<GenericResponse> response, final Object result) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getResponseCode()).isEqualTo("200");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("OK");
        assertThat(response.getBody().getErrorCode()).isNull();
        assertThat(response.getBody().getResponse()).isSameAs(result);
    }

    @Test
    void sendWrapsTheServiceResult() {
        final OtpSendRequest request = new OtpSendRequest().serviceName("LOGIN_OTP");
        final OtpSendResult result = new OtpSendResult().correlationId("cid").channels(List.of());
        when(otpService.send(request)).thenReturn(result);

        assertOk(new OtpController(otpService).sendOtp(request), result);
    }

    @Test
    void validateWrapsTheServiceResult() {
        final OtpValidateRequest request = new OtpValidateRequest().serviceName("LOGIN_OTP");
        final OtpValidateResult result = new OtpValidateResult().correlationId("cid").valid(true);
        when(otpService.validate(request)).thenReturn(result);

        assertOk(new OtpController(otpService).validateOtp(request), result);
    }

    @Test
    void configurationsWrapTheServiceResult() {
        final OtpConfigurationList result = new OtpConfigurationList().correlationId("cid").messages(List.of());
        when(otpService.configurations()).thenReturn(result);

        assertOk(new OtpController(otpService).getOtpConfigurations(), result);
    }

    @Test
    void everyOperationRequiresAppNotificationRole() throws NoSuchMethodException {
        for (Method method : new Method[]{OtpController.class.getMethod("sendOtp", OtpSendRequest.class),
                OtpController.class.getMethod("validateOtp", OtpValidateRequest.class),
                OtpController.class.getMethod("getOtpConfigurations")}) {
            final JwtSecured secured = method.getAnnotation(JwtSecured.class);
            assertThat(secured).isNotNull();
            assertThat(secured.roles()).containsExactly("APP_NOTIFICATION");
        }
    }

    @Test
    void legacyAliasesBindAndUnknownCorrelationIdIsIgnored() {
        final JsonMapper mapper = JsonMapper.builder().build();

        final OtpSendRequest send = mapper.readValue("{\"key\":\"LOGIN_OTP\",\"value\":\"user@example.com\","
                + "\"language\":\"EN\",\"ipAddress\":\"10.0.0.1\",\"correlationID\":\"x\"}", OtpSendRequest.class);
        assertThat(send.getServiceName()).isEqualTo("LOGIN_OTP");
        assertThat(send.getRecipients()).isEqualTo("user@example.com");

        final OtpValidateRequest validate = mapper.readValue("{\"key\":\"LOGIN_OTP\",\"value\":\"971500000010\","
                + "\"hashedOTP\":\"ABC\",\"correlationID\":\"x\"}", OtpValidateRequest.class);
        assertThat(validate.getServiceName()).isEqualTo("LOGIN_OTP");
        assertThat(validate.getRecipients()).isEqualTo("971500000010");
        assertThat(validate.getHashedOtp()).isEqualTo("ABC");
    }
}
