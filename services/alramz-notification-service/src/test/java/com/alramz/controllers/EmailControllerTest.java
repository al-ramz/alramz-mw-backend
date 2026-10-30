package com.alramz.controllers;

import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.EmailRequest;
import com.alramz.model.EmailSendResult;
import com.alramz.model.GenericResponse;
import com.alramz.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailControllerTest {

    @Mock
    private EmailService emailService;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void sendEmail_returnsOkEnvelopeWithRequestCorrelationId() {
        MDCUtil.putCorrelationId("caller-cid-42");
        final EmailRequest request = new EmailRequest().to("jane.doe@example.com");

        final ResponseEntity<GenericResponse> response = new EmailController(emailService).sendEmail(request);

        verify(emailService).sendEmail(request);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        final GenericResponse body = response.getBody();
        assertThat(body.getResponseCode()).isEqualTo("200");
        assertThat(body.getResponseMessage()).isEqualTo("OK");
        assertThat(body.getErrorCode()).isNull();
        assertThat(body.getErrorMsg()).isNull();
        assertThat(((EmailSendResult) body.getResponse()).getCorrelationId()).isEqualTo("caller-cid-42");
    }

    @Test
    void sendEmail_generatesCorrelationIdWhenMdcIsEmpty() {
        final GenericResponse body = new EmailController(emailService).sendEmail(new EmailRequest()).getBody();

        assertThat(((EmailSendResult) body.getResponse()).getCorrelationId()).hasSize(36);
    }

    @Test
    void sendEmail_requiresAppNotificationRole() throws NoSuchMethodException {
        final JwtSecured secured = EmailController.class.getMethod("sendEmail", EmailRequest.class)
                .getAnnotation(JwtSecured.class);

        assertThat(secured).isNotNull();
        assertThat(secured.roles()).containsExactly("APP_NOTIFICATION");
    }
}
