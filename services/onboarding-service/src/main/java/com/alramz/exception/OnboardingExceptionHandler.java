package com.alramz.exception;

import com.alramz.model.OnboardingResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = com.alramz.controllers.OnboardingController.class)
@Order(1)
public class OnboardingExceptionHandler {



    private String friendlyMessage(String field, String defaultMessage) {
        if (field == null) return defaultMessage;
        String normalized = field.contains(".") ? field.substring(field.lastIndexOf('.') + 1) : field;
        return switch (normalized) {
            case "custMobile", "cust_mobile" -> "Mobile Number (cust_mobile) is missing";
            case "custEmail", "cust_email" -> "Email Address (cust_email) is missing and Size of email must be between 1 and 255 in charecter length";
            case "custNin", "cust_nin" -> "NIN Number (cust_nin) is missing";
            case "kycMatch", "kyc_match" -> "Background check (kyc_match) is missing";
            case "fatcaUscitizen", "fatca_uscitizen" -> "USCitizen (fatca_uscitizen) is missing";
            default -> defaultMessage;
        };
    }

    private String mapFieldToInternalErrorCode(String field) {
        if (field == null) return "ONB011";
        String normalized = field.contains(".") ? field.substring(field.lastIndexOf('.') + 1) : field;
        return switch (normalized) {
            case "custMobile", "cust_mobile" -> "ONB001";
            case "custEmail", "cust_email" -> "ONB002";
            case "custNin", "cust_nin" -> "ONB003";
            case "kycMatch", "kyc_match" -> "ONB004";
            case "fatcaUscitizen", "fatca_uscitizen" -> "ONB005";
            default -> "ONB011";
        };
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<OnboardingResponse> handleApplication(ApplicationException ex, HttpServletRequest request) {
        java.util.UUID correlationId = extractCorrelationId(request);

        OnboardingResponse response = new OnboardingResponse();
        response.setResponseCode("400");
        response.setResponseMessage(ex.getMessage());
        response.setMemberReferenceNumber(correlationId);
        response.setInternalErrorCode(ex.getServiceErrorResponseCode());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<OnboardingResponse> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException ex, HttpServletRequest request) {
        java.util.UUID correlationId = extractCorrelationId(request);

        String friendly = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> friendlyMessage(fe.getField(), fe.getDefaultMessage()))
                .collect(java.util.stream.Collectors.joining("; "));

        OnboardingResponse response = new OnboardingResponse();
        response.setResponseCode("400");
        response.setResponseMessage(friendly);
        response.setMemberReferenceNumber(correlationId);
        String internalErrorCode = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> mapFieldToInternalErrorCode(fe.getField()))
                .filter(code -> !"ONB011".equals(code))
                .findFirst()
                .orElse("ONB011");
        response.setInternalErrorCode(internalErrorCode);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ExternalSystemException.class)
    public ResponseEntity<OnboardingResponse> handleExternalSystem(ExternalSystemException ex, HttpServletRequest request) {
        java.util.UUID correlationId = extractCorrelationId(request);

        OnboardingResponse response = new OnboardingResponse();
        response.setResponseCode("503");
        response.setResponseMessage(ex.getMessage());
        response.setMemberReferenceNumber(correlationId);
        response.setInternalErrorCode("ONB011");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OnboardingResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        java.util.UUID correlationId = extractCorrelationId(request);

        OnboardingResponse response = new OnboardingResponse();
        response.setResponseCode("500");
        response.setResponseMessage("Internal Server Error");
        response.setMemberReferenceNumber(correlationId);
        response.setInternalErrorCode("ONB012");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private java.util.UUID extractCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getParameter("correlationId");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }
        return java.util.UUID.fromString(correlationId);
    }
}
