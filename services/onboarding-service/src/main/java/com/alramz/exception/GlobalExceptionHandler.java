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
    private static final String DEFAULT_INTERNAL_ERROR_CODE = "ONB011";
    private static final String ERRORS_KEY = "errors";
    private static final String CODE_KEY = "code";
    private static final String BAD_REQUEST_CODE = "400";
    private static final String SERVICE_UNAVAILABLE_CODE = "503";
    private static final String MESSAGE_KEY = "message";
    private static final String INVALID_REQUEST_MESSAGE = "Invalid request";
    private static final String MALFORMED_JSON_MESSAGE = "Malformed JSON request";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error";

    private String friendlyMessage(String field, String defaultMessage) {
        if (field == null) {
            return defaultMessage;
        }
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
        if (field == null) {
            return DEFAULT_INTERNAL_ERROR_CODE;
        }
        String normalized = field.contains(".") ? field.substring(field.lastIndexOf('.') + 1) : field;
        return switch (normalized) {
            case "custMobile", "cust_mobile" -> "ONB001";
            case "custEmail", "cust_email" -> "ONB002";
            case "custNin", "cust_nin" -> "ONB003";
            case "kycMatch", "kyc_match" -> "ONB004";
            case "fatcaUscitizen", "fatca_uscitizen" -> "ONB005";
            default -> DEFAULT_INTERNAL_ERROR_CODE;
        };
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .collect(Collectors.joining("; "));

        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode(BAD_REQUEST_CODE);
            apiResponse.setResponseMessage(message);
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode(BAD_REQUEST_CODE);
            String friendly = ex.getBindingResult().getFieldErrors().stream()
                    .map(fe -> friendlyMessage(fe.getField(), fe.getDefaultMessage()))
                    .collect(Collectors.joining("; "));
            response.setResponseMessage(friendly.isBlank() ? message : friendly);
            response.setMemberReferenceNumber(extractCorrelationId(request));
            String internalErrorCode = ex.getBindingResult().getFieldErrors().stream()
                    .map(fe -> mapFieldToInternalErrorCode(fe.getField()))
                    .filter(code -> !DEFAULT_INTERNAL_ERROR_CODE.equals(code))
                    .findFirst()
                    .orElse(DEFAULT_INTERNAL_ERROR_CODE);
            response.setInternalErrorCode(internalErrorCode);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Map<String, Object> response = Map.of(
                ERRORS_KEY, List.of(Map.of(CODE_KEY, BAD_REQUEST_CODE, MESSAGE_KEY, message))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode(BAD_REQUEST_CODE);
        apiResponse.setResponseMessage(INVALID_REQUEST_MESSAGE);
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
            apiResponse.setResponseCode(BAD_REQUEST_CODE);
            apiResponse.setResponseMessage(message);
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode(BAD_REQUEST_CODE);
            String friendly = ex.getConstraintViolations().stream()
                    .map(cv -> friendlyMessage(cv.getPropertyPath().toString(), cv.getMessage()))
                    .collect(Collectors.joining("; "));
            response.setResponseMessage(friendly.isBlank() ? message : friendly);
            response.setMemberReferenceNumber(extractCorrelationId(request));
            String internalErrorCode = ex.getConstraintViolations().stream()
                    .map(cv -> mapFieldToInternalErrorCode(cv.getPropertyPath().toString()))
                    .filter(code -> !DEFAULT_INTERNAL_ERROR_CODE.equals(code))
                    .findFirst()
                    .orElse(DEFAULT_INTERNAL_ERROR_CODE);
            response.setInternalErrorCode(internalErrorCode);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Map<String, Object> response = Map.of(
                ERRORS_KEY, List.of(Map.of(CODE_KEY, BAD_REQUEST_CODE, MESSAGE_KEY, message))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode(BAD_REQUEST_CODE);
        apiResponse.setResponseMessage(INVALID_REQUEST_MESSAGE);
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode(BAD_REQUEST_CODE);
            apiResponse.setResponseMessage(MALFORMED_JSON_MESSAGE);
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode(BAD_REQUEST_CODE);
            response.setResponseMessage(MALFORMED_JSON_MESSAGE);
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode(DEFAULT_INTERNAL_ERROR_CODE);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Map<String, Object> response = Map.of(
                ERRORS_KEY, List.of(Map.of(CODE_KEY, BAD_REQUEST_CODE, MESSAGE_KEY, MALFORMED_JSON_MESSAGE))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode(BAD_REQUEST_CODE);
        apiResponse.setResponseMessage(INVALID_REQUEST_MESSAGE);
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    @ExceptionHandler(IbanValidationException.class)
    public ResponseEntity<GenericResponse> handleIbanValidation(IbanValidationException ex) {
        Map<String, Object> response = Map.of(
                ERRORS_KEY, List.of(Map.of(CODE_KEY, ex.getCode(), MESSAGE_KEY, ex.getMessage()))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode(BAD_REQUEST_CODE);
        apiResponse.setResponseMessage(INVALID_REQUEST_MESSAGE);
        apiResponse.setResponse(response);

        return ResponseEntity.ok(apiResponse);
    }

    @ExceptionHandler(ExternalSystemException.class)
    public ResponseEntity<Object> handleExternalSystem(ExternalSystemException ex, HttpServletRequest request) {
        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode(SERVICE_UNAVAILABLE_CODE);
            apiResponse.setResponseMessage(ex.getMessage());
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode(SERVICE_UNAVAILABLE_CODE);
            response.setResponseMessage(ex.getMessage());
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode(DEFAULT_INTERNAL_ERROR_CODE);

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        Map<String, Object> response = Map.of(
                ERRORS_KEY, List.of(Map.of(CODE_KEY, SERVICE_UNAVAILABLE_CODE, MESSAGE_KEY, ex.getMessage()))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode(SERVICE_UNAVAILABLE_CODE);
        apiResponse.setResponseMessage(ex.getMessage());
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
    }

    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<Object> handleTechnical(TechnicalException ex, HttpServletRequest request) {
        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode(SERVICE_UNAVAILABLE_CODE);
            apiResponse.setResponseMessage(ex.getMessage());
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode(SERVICE_UNAVAILABLE_CODE);
            response.setResponseMessage(ex.getMessage());
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode(DEFAULT_INTERNAL_ERROR_CODE);

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        Map<String, Object> response = Map.of(
                ERRORS_KEY, List.of(Map.of(CODE_KEY, SERVICE_UNAVAILABLE_CODE, MESSAGE_KEY, ex.getMessage()))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode(SERVICE_UNAVAILABLE_CODE);
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
            apiResponse.setResponseCode(BAD_REQUEST_CODE);
            apiResponse.setResponseMessage(message);
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode(BAD_REQUEST_CODE);
            response.setResponseMessage(message);
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode(DEFAULT_INTERNAL_ERROR_CODE);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Map<String, Object> response = Map.of(
                ERRORS_KEY, List.of(Map.of(CODE_KEY, BAD_REQUEST_CODE, MESSAGE_KEY, message))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode(BAD_REQUEST_CODE);
        apiResponse.setResponseMessage(INVALID_REQUEST_MESSAGE);
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    @ExceptionHandler(ApiCallFailedException.class)
    public ResponseEntity<GenericResponse> handleApiCallFailed(ApiCallFailedException ex) {
        Map<String, Object> response = Map.of(
                ERRORS_KEY, List.of(Map.of(CODE_KEY, SERVICE_UNAVAILABLE_CODE, MESSAGE_KEY, "IBAN validation service unavailable"))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode(SERVICE_UNAVAILABLE_CODE);
        apiResponse.setResponseMessage("IBAN validation service unavailable");
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
    }

    @ExceptionHandler(InvalidHttpRequestException.class)
    public ResponseEntity<Object> handleInvalidHttpRequest(InvalidHttpRequestException ex, HttpServletRequest request) {
        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode(SERVICE_UNAVAILABLE_CODE);
            apiResponse.setResponseMessage(ex.getMessage());
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode(SERVICE_UNAVAILABLE_CODE);
            response.setResponseMessage(ex.getMessage());
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode(DEFAULT_INTERNAL_ERROR_CODE);

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        Map<String, Object> response = Map.of(
                ERRORS_KEY, List.of(Map.of(CODE_KEY, SERVICE_UNAVAILABLE_CODE, MESSAGE_KEY, ex.getMessage()))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode(SERVICE_UNAVAILABLE_CODE);
        apiResponse.setResponseMessage(ex.getMessage());
        apiResponse.setResponse(response);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex, HttpServletRequest request) {
        if (isValidationRequest(request)) {
            GenericResponse apiResponse = new GenericResponse();
            apiResponse.setResponseCode(SERVICE_UNAVAILABLE_CODE);
            apiResponse.setResponseMessage(INTERNAL_SERVER_ERROR_MESSAGE);
            apiResponse.setResponse(null);
            apiResponse.setCorrelationId(extractCorrelationId(request));

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiResponse);
        }

        if (isOnboardingRequest(request)) {
            OnboardingResponse response = new OnboardingResponse();
            response.setResponseCode("500");
            response.setResponseMessage("Internal Server Error");
            response.setMemberReferenceNumber(extractCorrelationId(request));
            response.setInternalErrorCode(DEFAULT_INTERNAL_ERROR_CODE);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        Map<String, Object> response = Map.of(
                ERRORS_KEY, List.of(Map.of(CODE_KEY, SERVICE_UNAVAILABLE_CODE, MESSAGE_KEY, INTERNAL_SERVER_ERROR_MESSAGE))
        );

        GenericResponse apiResponse = new GenericResponse();
        apiResponse.setResponseCode(SERVICE_UNAVAILABLE_CODE);
        apiResponse.setResponseMessage(INTERNAL_SERVER_ERROR_MESSAGE);
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
