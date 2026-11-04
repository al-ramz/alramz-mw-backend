package com.alramz.exception;

import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    @ParameterizedTest
    @CsvSource({
            "CLS001, 502, Bad Gateway, Country data provider returned an error",
            "CLS002, 502, Bad Gateway, Country data provider returned an invalid response",
            "CLS003, 503, Service Unavailable, Country data provider unavailable",
            "CLS004, 500, Internal Server Error, Internal Server Error",
            "CRT001, 502, Bad Gateway, Country data provider returned an error",
            "CRT002, 502, Bad Gateway, Country data provider returned an invalid response",
            "CRT003, 503, Service Unavailable, Country data provider unavailable",
            "CRT004, 500, Internal Server Error, Internal Server Error"
    })
    void handleRiskCompliance_shouldMapEachCode(ErrorCode code, int status, String reason, String message) {
        String correlationId = UUID.randomUUID().toString();
        MDCUtil.putCorrelationId(correlationId);

        ResponseEntity<GenericResponse> response =
                handler.handleRiskCompliance(new RiskComplianceException(code, new IllegalStateException("secret")));

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        GenericResponse body = response.getBody();
        assertThat(body.getResponseCode()).isEqualTo(String.valueOf(status));
        assertThat(body.getResponseMessage()).isEqualTo(reason);
        assertThat(body.getErrorCode()).isEqualTo(code.name());
        assertThat(body.getErrorMsg()).isEqualTo(message);
        assertThat(body.getResponse()).isNull();
        assertThat(body.getCorrelationId()).isEqualTo(UUID.fromString(correlationId));
    }

    @Test
    void handleAccessDenied_shouldReturn403() {
        ResponseEntity<GenericResponse> response = handler.handleAccessDenied(new AccessDeniedException("no role"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getResponseCode()).isEqualTo("403");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Forbidden");
        assertThat(response.getBody().getErrorCode()).isNull();
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Access denied");
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }

    @Test
    void handleGeneric_shouldKeepAFrameworkStatusInsteadOfReturning500() {
        ResponseEntity<GenericResponse> response =
                handler.handleGeneric(new HttpRequestMethodNotSupportedException("PUT", java.util.List.of("GET", "POST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getResponseCode()).isEqualTo("405");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Method Not Allowed");
        assertThat(response.getBody().getErrorCode()).isNull();
        assertThat(response.getHeaders().getAllow()).isNotEmpty();
    }

    @Test
    void handleGeneric_shouldReturnAGeneric500WithoutLeakingTheMessage() {
        ResponseEntity<GenericResponse> response = handler.handleGeneric(new IllegalStateException("db password=x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isNull();
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().toString()).doesNotContain("password");
    }
}
