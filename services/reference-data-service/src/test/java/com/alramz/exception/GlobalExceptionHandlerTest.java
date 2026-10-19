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
    void handleValidation_shouldReturnBadRequestWithFieldDescribingMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError(
                "request", "field", "", false, null, null,
                "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<GenericResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("400");
        assertThat(response.getBody().getResponseMessage()).contains("field");
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
