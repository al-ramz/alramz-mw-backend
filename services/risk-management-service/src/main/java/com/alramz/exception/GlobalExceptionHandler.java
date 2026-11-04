package com.alramz.exception;

import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "com.alramz.controllers")
@Order(0)
public class GlobalExceptionHandler {

    @ExceptionHandler(RiskComplianceException.class)
    public ResponseEntity<GenericResponse> handleRiskCompliance(RiskComplianceException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("Risk compliance request failed with {}", code.code(), ex);
        return ResponseEntity.status(code.status()).body(body(code.status(), code.code(), code.message()));
    }

    // Must be explicit: otherwise the catch-all below turns @JwtSecured's 403 into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(HttpStatus.FORBIDDEN, null, "Access denied"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGeneric(Exception ex) {
        // ErrorResponse is an interface, not a Throwable, so it cannot be an @ExceptionHandler type.
        // Framework errors (405/415/400 ...) carry their own status: keep it instead of returning 500.
        if (ex instanceof ErrorResponse errorResponse) {
            return frameworkResponse(errorResponse);
        }
        // Never leak the message or stack trace of an unexpected failure to the caller.
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, null, "Internal Server Error"));
    }

    static ResponseEntity<GenericResponse> frameworkResponse(ErrorResponse errorResponse) {
        HttpStatusCode status = errorResponse.getStatusCode();
        return ResponseEntity.status(status)
                .headers(errorResponse.getHeaders())
                .body(body(status, null, reasonPhrase(status)));
    }

    static GenericResponse body(HttpStatusCode status, String errorCode, String errorMsg) {
        GenericResponse body = new GenericResponse();
        body.setResponseCode(String.valueOf(status.value()));
        body.setResponseMessage(reasonPhrase(status));
        body.setErrorCode(errorCode);
        body.setErrorMsg(errorMsg);
        body.setCorrelationId(MDCUtil.getCorrelationIdAsUuid());
        return body;
    }

    private static String reasonPhrase(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.getReasonPhrase() : String.valueOf(status.value());
    }
}
