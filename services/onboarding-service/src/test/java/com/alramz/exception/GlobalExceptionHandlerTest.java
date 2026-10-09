package com.alramz.exception;

import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.exceptions.InvalidHttpRequestException;
import com.alramz.model.GenericResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void handleValidation_shouldReturnBadRequest() {
        MethodArgumentNotValidException ex = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult bindingResult = org.mockito.Mockito.mock(org.springframework.validation.BindingResult.class);
        org.springframework.validation.FieldError fieldError = new org.springframework.validation.FieldError("field", "field", "default message");
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        when(request.getRequestURI()).thenReturn("/api/v1/existing-data/validation");
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);
        
        ResponseEntity<Object> response = handler.handleValidation(ex, request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(GenericResponse.class);
        GenericResponse body = (GenericResponse) response.getBody();
        assertThat(body.getResponseCode()).isEqualTo("400");
    }

    @Test
    void handleConstraint_shouldReturnBadRequest() {
        ConstraintViolationException ex = new ConstraintViolationException("constraint", Set.of());
        when(request.getRequestURI()).thenReturn("/api/v1/existing-data/validation");
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);
        
        ResponseEntity<Object> response = handler.handleConstraint(ex, request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(GenericResponse.class);
    }

    @Test
    void handleIbanValidation_shouldReturnBadRequest() {
        IbanValidationException ex = new IbanValidationException("1069", "validation error");
        
        ResponseEntity<GenericResponse> response = handler.handleIbanValidation(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResponseCode()).isEqualTo("400");
    }

    @Test
    void handleExternalSystem_shouldReturnServiceUnavailable() {
        ExternalSystemException ex = new ExternalSystemException("external error");
        when(request.getRequestURI()).thenReturn("/api/v1/existing-data/validation");
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);
        
        ResponseEntity<Object> response = handler.handleExternalSystem(ex, request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isInstanceOf(GenericResponse.class);
    }

    @Test
    void handleTechnical_shouldReturnServiceUnavailable() {
        TechnicalException ex = new TechnicalException("technical error");
        when(request.getRequestURI()).thenReturn("/api/v1/existing-data/validation");
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);
        
        ResponseEntity<Object> response = handler.handleTechnical(ex, request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isInstanceOf(GenericResponse.class);
    }

    @Test
    void handleApplication_shouldReturnBadRequest() {
        ApplicationException ex = new ApplicationException("field", "test message");
        when(request.getRequestURI()).thenReturn("/api/v1/existing-data/validation");
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);
        
        ResponseEntity<Object> response = handler.handleApplication(ex, request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(GenericResponse.class);
        GenericResponse body = (GenericResponse) response.getBody();
        assertThat(body.getResponseCode()).isEqualTo("400");
    }

    @Test
    void handleApiCallFailed_shouldReturnServiceUnavailable() {
        ApiCallFailedException ex = new ApiCallFailedException("/path", "POST", 500, "error");
        
        ResponseEntity<GenericResponse> response = handler.handleApiCallFailed(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void handleInvalidHttpRequest_shouldReturnServiceUnavailable() {
        InvalidHttpRequestException ex = new InvalidHttpRequestException("invalid request");
        when(request.getRequestURI()).thenReturn("/api/v1/existing-data/validation");
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);
        
        ResponseEntity<Object> response = handler.handleInvalidHttpRequest(ex, request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isInstanceOf(GenericResponse.class);
    }

    @Test
    void handleGeneric_shouldReturnServiceUnavailable() {
        Exception ex = new Exception("generic error");
        when(request.getRequestURI()).thenReturn("/api/v1/existing-data/validation");
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);
        
        ResponseEntity<Object> response = handler.handleGeneric(ex, request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isInstanceOf(GenericResponse.class);
    }
}
