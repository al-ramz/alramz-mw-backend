package com.alramz.controllers;

import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.model.EmailSendResult;
import com.alramz.model.GenericResponse;
import com.alramz.model.TemplatedEmailRequest;
import com.alramz.service.TemplatedEmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplatedEmailControllerTest {

    @Mock
    private TemplatedEmailService service;

    @Test
    void sendTemplatedEmail_returnsOkEnvelopeWithServiceCorrelationIdAndPassesClientIp() {
        final MockHttpServletRequest http = new MockHttpServletRequest();
        http.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        final TemplatedEmailRequest request = new TemplatedEmailRequest().templateName("STATEMENT");
        when(service.send(request, "203.0.113.9")).thenReturn("cid-77");

        final ResponseEntity<GenericResponse> response =
                new TemplatedEmailController(service, http).sendTemplatedEmail(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        final GenericResponse body = response.getBody();
        assertThat(body.getResponseCode()).isEqualTo("200");
        assertThat(body.getResponseMessage()).isEqualTo("OK");
        assertThat(body.getErrorCode()).isNull();
        assertThat(body.getErrorMsg()).isNull();
        assertThat(((EmailSendResult) body.getResponse()).getCorrelationId()).isEqualTo("cid-77");
    }

    @Test
    void sendTemplatedEmail_requiresAppNotificationRole() throws NoSuchMethodException {
        final JwtSecured secured = TemplatedEmailController.class
                .getMethod("sendTemplatedEmail", TemplatedEmailRequest.class).getAnnotation(JwtSecured.class);

        assertThat(secured).isNotNull();
        assertThat(secured.roles()).containsExactly("APP_NOTIFICATION");
    }
}
