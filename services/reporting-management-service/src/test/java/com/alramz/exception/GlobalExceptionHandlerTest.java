package com.alramz.exception;

import com.alramz.model.GenericResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRelationshipManagerLookup_shouldReturnInternalServerErrorWithGeneratedCorrelationId() {
        RelationshipManagerLookupException ex =
                new RelationshipManagerLookupException("Unable to retrieve relationship manager data");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);

        ResponseEntity<GenericResponse> response = handler.handleRelationshipManagerLookup(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("500");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Unable to retrieve relationship manager data");
        assertThat(response.getBody().getResponse()).isNull();
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }

    @Test
    void handleRelationshipManagerLookup_shouldUseCorrelationIdFromHeader() {
        RelationshipManagerLookupException ex = new RelationshipManagerLookupException("boom");
        HttpServletRequest request = mock(HttpServletRequest.class);
        String correlationId = "550e8400-e29b-41d4-a716-446655440000";
        when(request.getHeader("X-Correlation-Id")).thenReturn(correlationId);

        ResponseEntity<GenericResponse> response = handler.handleRelationshipManagerLookup(ex, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCorrelationId()).hasToString(correlationId);
    }

    @Test
    void handleInvalidDateRange_shouldReturnBadRequestWithNoCorrelationId() {
        InvalidDateRangeException ex =
                new InvalidDateRangeException("startDate: must be blank or a valid ISO-8601 date (YYYY-MM-DD)");

        ResponseEntity<GenericResponse> response = handler.handleInvalidDateRange(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("400");
        assertThat(response.getBody().getResponseMessage())
                .isEqualTo("startDate: must be blank or a valid ISO-8601 date (YYYY-MM-DD)");
        assertThat(response.getBody().getResponse()).isNull();
        assertThat(response.getBody().getCorrelationId()).isNull();
    }

    @Test
    void handleValidation_shouldReturnBadRequestWithFieldDescribingMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError(
                "commissionsByRelationshipManagerRequest", "startDate", "", false, null, null,
                "must match \"^$|\\d{4}-\\d{2}-\\d{2}\"");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<GenericResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("400");
        assertThat(response.getBody().getResponseMessage()).contains("startDate");
        assertThat(response.getBody().getResponse()).isNull();
    }

    @Test
    void handleNotReadable_shouldReturnBadRequestWithMalformedJsonMessage() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        ResponseEntity<GenericResponse> response = handler.handleNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("400");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Malformed JSON request");
        assertThat(response.getBody().getResponse()).isNull();
    }

    @Test
    void handleGeneric_shouldReturnInternalServerErrorWithoutLeakingExceptionDetail() {
        Exception ex = new Exception("some internal detail that should not leak");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);

        ResponseEntity<GenericResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("500");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Internal server error");
        assertThat(response.getBody().getResponseMessage()).doesNotContain("some internal detail");
        assertThat(response.getBody().getResponse()).isNull();
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }
}
