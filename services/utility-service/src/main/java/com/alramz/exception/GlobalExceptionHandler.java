package com.alramz.exception;

import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.alramz.controllers")
@Order(0)
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<GenericResponse> handleApplication(ApplicationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .collect(Collectors.joining("; "));
        ErrorCode errorCode = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> ErrorCode.forField(fe.getField()))
                .orElse(ErrorCode.INVALID_REQUEST);
        return buildResponse(HttpStatus.BAD_REQUEST, errorCode, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "Malformed JSON request");
    }

    @ExceptionHandler(FinouxIntegrationException.class)
    public ResponseEntity<GenericResponse> handleFinouxIntegration(FinouxIntegrationException ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(FinouxUnavailableException.class)
    public ResponseEntity<GenericResponse> handleFinouxUnavailable(FinouxUnavailableException ex) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.FINOUX_UNAVAILABLE, ex.getMessage());
    }

    // Must be explicit: otherwise the catch-all below turns @JwtSecured's 403 into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, null, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGeneric(Exception ex) {
        // Never leak the message or stack trace of an unexpected failure.
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Internal Server Error");
    }

    private ResponseEntity<GenericResponse> buildResponse(HttpStatus status, ErrorCode errorCode, String errorMsg) {
        GenericResponse body = new GenericResponse();
        body.setResponseCode(String.valueOf(status.value()));
        body.setResponseMessage(status.getReasonPhrase());
        body.setErrorCode(errorCode != null ? errorCode.code() : null);
        body.setErrorMsg(errorMsg);
        body.setCorrelationId(MDCUtil.getCorrelationIdAsUuid());
        return ResponseEntity.status(status).body(body);
    }
}
