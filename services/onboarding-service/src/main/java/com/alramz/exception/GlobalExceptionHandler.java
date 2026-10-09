package com.alramz.exception;

import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.exceptions.InvalidHttpRequestException;
import com.alramz.model.GenericResponse;
import com.alramz.model.OnboardingResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.alramz.controllers")
@Order(0)
public class GlobalExceptionHandler {

    private static final String VALIDATION_PATH = "/api/v1/existing-data/validation";
    private static final String ONBOARDING_PATH = "/api/v1/dfm/onboarding";

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
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .collect(Collectors.joining("; "));

        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode("400");
            apiResponse.setResponseMessage(message);
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode("400");
            String friendly = ex.getBindingResult().getFieldErrors().stream()
                    .map(fe -> friendlyMessage(fe.getField(), fe.getDefaultMessage()))
                    .collect(Collectors.joining("; "));
            response.setResponseMessage(friendly.isBlank() ? message : friendly);
            response.setMemberReferenceNumber(extractCorrelationId(request));
            String internalErrorCode = ex.getBindingResult().getFieldErrors().stream()
                    .map(fe -> mapFieldToInternalErrorCode(fe.getField()))
                    .filter(code -> !"ONB011".equals(code))
                    .findFirst()
                    .orElse("ONB011");
            response.setInternalErrorCode(internalErrorCode);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Map<String, Object> response = Map.of(
                "errors", List.of(Map.of("code", "400", "message", message))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode("400");
        apiResponse.setResponseMessage("Invalid request");
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode("400");
            apiResponse.setResponseMessage(message);
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode("400");
            String friendly = ex.getConstraintViolations().stream()
                    .map(cv -> friendlyMessage(cv.getPropertyPath().toString(), cv.getMessage()))
                    .collect(Collectors.joining("; "));
            response.setResponseMessage(friendly.isBlank() ? message : friendly);
            response.setMemberReferenceNumber(extractCorrelationId(request));
            String internalErrorCode = ex.getConstraintViolations().stream()
                    .map(cv -> mapFieldToInternalErrorCode(cv.getPropertyPath().toString()))
                    .filter(code -> !"ONB011".equals(code))
                    .findFirst()
                    .orElse("ONB011");
            response.setInternalErrorCode(internalErrorCode);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Map<String, Object> response = Map.of(
                "errors", List.of(Map.of("code", "400", "message", message))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode("400");
        apiResponse.setResponseMessage("Invalid request");
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode("400");
            apiResponse.setResponseMessage("Malformed JSON request");
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode("400");
            response.setResponseMessage("Malformed JSON request");
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode("ONB011");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Map<String, Object> response = Map.of(
                "errors", List.of(Map.of("code", "400", "message", "Malformed JSON request"))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode("400");
        apiResponse.setResponseMessage("Invalid request");
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    @ExceptionHandler(IbanValidationException.class)
    public ResponseEntity<GenericResponse> handleIbanValidation(IbanValidationException ex) {
        Map<String, Object> response = Map.of(
                "errors", List.of(Map.of("code", ex.getCode(), "message", ex.getMessage()))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode("400");
        apiResponse.setResponseMessage("Invalid request");
        apiResponse.setResponse(response);

        return ResponseEntity.ok(apiResponse);
    }

    @ExceptionHandler(ExternalSystemException.class)
    public ResponseEntity<Object> handleExternalSystem(ExternalSystemException ex, HttpServletRequest request) {
        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode("503");
            apiResponse.setResponseMessage(ex.getMessage());
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode("503");
            response.setResponseMessage(ex.getMessage());
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode("ONB011");

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        Map<String, Object> response = Map.of(
                "errors", List.of(Map.of("code", "503", "message", ex.getMessage()))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode("503");
        apiResponse.setResponseMessage(ex.getMessage());
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
    }

    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<Object> handleTechnical(TechnicalException ex, HttpServletRequest request) {
        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode("503");
            apiResponse.setResponseMessage(ex.getMessage());
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode("503");
            response.setResponseMessage(ex.getMessage());
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode("ONB011");

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        Map<String, Object> response = Map.of(
                "errors", List.of(Map.of("code", "503", "message", ex.getMessage()))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode("503");
        apiResponse.setResponseMessage(ex.getMessage());
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Object> handleApplication(ApplicationException ex, HttpServletRequest request) {
        String message = ex.getField() != null
                ? ex.getField() + ": " + ex.getMessage()
                : ex.getMessage();

        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode("400");
            apiResponse.setResponseMessage(message);
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode("400");
            response.setResponseMessage(message);
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode("ONB011");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Map<String, Object> response = Map.of(
                "errors", List.of(Map.of("code", "400", "message", message))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode("400");
        apiResponse.setResponseMessage("Invalid request");
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    @ExceptionHandler(ApiCallFailedException.class)
    public ResponseEntity<GenericResponse> handleApiCallFailed(ApiCallFailedException ex) {
        Map<String, Object> response = Map.of(
                "errors", List.of(Map.of("code", "503", "message", "IBAN validation service unavailable"))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode("503");
        apiResponse.setResponseMessage("IBAN validation service unavailable");
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
    }

    @ExceptionHandler(InvalidHttpRequestException.class)
    public ResponseEntity<Object> handleInvalidHttpRequest(InvalidHttpRequestException ex, HttpServletRequest request) {
        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode("503");
            apiResponse.setResponseMessage(ex.getMessage());
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode("503");
            response.setResponseMessage(ex.getMessage());
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode("ONB011");

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        Map<String, Object> response = Map.of(
                "errors", List.of(Map.of("code", "503", "message", ex.getMessage()))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode("503");
        apiResponse.setResponseMessage(ex.getMessage());
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex, HttpServletRequest request) {
        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode("503");
            apiResponse.setResponseMessage("Internal server error");
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode("500");
            response.setResponseMessage("Internal Server Error");
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode("ONB011");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        Map<String, Object> response = Map.of(
                "errors", List.of(Map.of("code", "503", "message", "Internal server error"))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode("503");
        apiResponse.setResponseMessage("Internal server error");
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
    }

    private boolean isValidationRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith(VALIDATION_PATH);
    }

    private boolean isOnboardingRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith(ONBOARDING_PATH);
    }

    private UUID extractCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getParameter("correlationId");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return UUID.fromString(correlationId);
    }
}
