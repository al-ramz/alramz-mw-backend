package com.alramz.logging.exception;

import com.alramz.logging.constants.LoggingConstants;
import com.alramz.logging.util.MDCUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized, last-resort exception logger. Registered at the lowest
 * precedence so that application-provided exception handlers always take
 * priority; this handler only acts on exceptions that escape every other
 * handler, guaranteeing each uncaught exception is logged exactly once.
 * <p>
 * Stack traces are emitted at {@code ERROR} level and never include sensitive
 * data. The correlation id, request URI and HTTP method are attached to every
 * error log.
 */
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "company.logging.exception.enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE)
public class LoggingExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(LoggingExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String correlationId = MDCUtil.getCorrelationId();
        String uri = request != null ? request.getRequestURI() : null;
        String method = request != null ? request.getMethod() : null;

        if (uri != null && method != null) {
            logger.warn("Bad request: {} {} (correlationId={})", method, uri, correlationId, ex);
        } else {
            logger.warn("Bad request (correlationId={})", correlationId, ex);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 400);
        body.put("error", ex.getMessage());
        body.put("message", ex.getMessage());
        body.put(LoggingConstants.CORRELATION_ID, correlationId);
        return ResponseEntity.status(400).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception ex, HttpServletRequest request) {
        String correlationId = MDCUtil.getCorrelationId();
        String uri = request != null ? request.getRequestURI() : null;
        String method = request != null ? request.getMethod() : null;

        if (uri != null && method != null) {
            logger.error("Uncaught exception while processing {} {} (correlationId={})", method, uri, correlationId, ex);
        } else {
            logger.error("Uncaught exception (correlationId={})", correlationId, ex);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 500);
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred. Reference: " + correlationId);
        body.put(LoggingConstants.CORRELATION_ID, correlationId);
        return ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
