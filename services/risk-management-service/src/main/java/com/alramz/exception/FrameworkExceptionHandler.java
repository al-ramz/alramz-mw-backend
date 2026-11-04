package com.alramz.exception;

import com.alramz.controllers.RiskComplianceLegacyController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Handles framework errors raised during handler mapping (no handler chosen yet). Spring skips every
 * basePackages-scoped advice for those, so GlobalExceptionHandler never sees them and the starter's
 * LoggingExceptionHandler would answer HTTP 500 (e.g. for {@code PUT /api/v1/countries}). Deliberately
 * unscoped and limited to these four types, with no catch-all, so everything else still reaches the
 * starter safety net unchanged. Order 1: after GlobalExceptionHandler, before the starter.
 */
@RestControllerAdvice
@Order(1)
public class FrameworkExceptionHandler {

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class, NoResourceFoundException.class})
    public ResponseEntity<Object> handleFrameworkException(Exception ex, HttpServletRequest request) {
        if (RiskComplianceLegacyController.isLegacyRequest(request)) {
            return RiskComplianceLegacyController.legacyInternalError(request);
        }
        // All four declared types implement ErrorResponse today; fail closed (generic 500) if that ever changes.
        if (!(ex instanceof ErrorResponse errorResponse)) {
            return ResponseEntity.internalServerError()
                    .body(GlobalExceptionHandler.body(HttpStatus.INTERNAL_SERVER_ERROR, null, "Internal Server Error"));
        }
        ResponseEntity<?> response = GlobalExceptionHandler.frameworkResponse(errorResponse);
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(response.getBody());
    }
}
