package com.alramz.exception;

import com.alramz.model.OnboardingResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnboardingExceptionHandlerTest {

    private final OnboardingExceptionHandler handler = new OnboardingExceptionHandler();

    @Test
    void handleApplication_shouldReturnBadRequestWithErrorCode() {
        ApplicationException ex = new ApplicationException("cust_mobile", "Mobile Number (cust_mobile) is missing", "ONB001");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);

        ResponseEntity<OnboardingResponse> response = handler.handleApplication(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("400");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Mobile Number (cust_mobile) is missing");
        assertThat(response.getBody().getMemberReferenceNumber()).isNotNull();
        assertThat(response.getBody().getInternalErrorCode()).isEqualTo("ONB001");
    }

    @Test
    void handleValidation_shouldReturnBadRequestWithONB001() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("onboardingRequest", "custMobile", "", false, null, null, "size must be between 1 and 2147483647");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);

        ResponseEntity<OnboardingResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("400");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Mobile Number (cust_mobile) is missing");
        assertThat(response.getBody().getMemberReferenceNumber()).isNotNull();
        assertThat(response.getBody().getInternalErrorCode()).isEqualTo("ONB001");
    }

    @Test
    void handleValidation_shouldReturnBadRequestWithONB002() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("onboardingRequest", "custEmail", "", false, null, null, "size must be between 1 and 2147483647");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);

        ResponseEntity<OnboardingResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("400");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Email Address (cust_email) is missing and Size of email must be between 1 and 255 in charecter length");
        assertThat(response.getBody().getMemberReferenceNumber()).isNotNull();
        assertThat(response.getBody().getInternalErrorCode()).isEqualTo("ONB002");
    }

    @Test
    void handleExternalSystem_shouldReturnServiceUnavailable() {
        ExternalSystemException ex = new ExternalSystemException("external error");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);

        ResponseEntity<OnboardingResponse> response = handler.handleExternalSystem(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("503");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("external error");
        assertThat(response.getBody().getMemberReferenceNumber()).isNotNull();
        assertThat(response.getBody().getInternalErrorCode()).isEqualTo("ONB011");
    }

    @Test
    void handleGeneric_shouldReturnInternalServerError() {
        Exception ex = new Exception("generic error");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);

        ResponseEntity<OnboardingResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("500");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getMemberReferenceNumber()).isNotNull();
        assertThat(response.getBody().getInternalErrorCode()).isEqualTo("ONB012");
    }
}
