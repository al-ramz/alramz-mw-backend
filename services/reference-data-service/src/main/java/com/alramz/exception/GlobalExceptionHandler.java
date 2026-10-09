package com.alramz.exception;

import com.alramz.model.GenericResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * First service-owned exception handler for {@code reference-data-service}.
 * Covers every controller under {@code com.alramz.controllers} (including the
 * pre-existing {@code RedisCacheController}/{@code ApplicationController}), since this
 * service has exactly one response envelope family ({@link GenericResponse}) — no
 * per-controller path branching is needed, unlike onboarding-service's
 * {@code GlobalExceptionHandler}.
 */
@RestControllerAdvice(basePackages = "com.alramz.controllers")
@Order(0)
public class GlobalExceptionHandler {

    @ExceptionHandler(RelationshipManagerLookupException.class)
    public ResponseEntity<GenericResponse> handleRelationshipManagerLookup(
            RelationshipManagerLookupException ex, HttpServletRequest request) {
        GenericResponse response = new GenericResponse();
        response.setResponseCode("500");
        response.setResponseMessage("Unable to retrieve relationship manager data");
        response.setResponse(null);
        response.setCorrelationId(extractCorrelationId(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<GenericResponse> handleInvalidDateRange(InvalidDateRangeException ex) {
        GenericResponse response = new GenericResponse();
        response.setResponseCode("400");
        response.setResponseMessage(ex.getMessage());
        response.setResponse(null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .collect(Collectors.joining("; "));

        GenericResponse response = new GenericResponse();
        response.setResponseCode("400");
        response.setResponseMessage(message.isBlank() ? "Invalid request" : message);
        response.setResponse(null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        GenericResponse response = new GenericResponse();
        response.setResponseCode("400");
        response.setResponseMessage("Malformed JSON request");
        response.setResponse(null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        GenericResponse response = new GenericResponse();
        response.setResponseCode("500");
        response.setResponseMessage("Internal server error");
        response.setResponse(null);
        response.setCorrelationId(extractCorrelationId(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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
