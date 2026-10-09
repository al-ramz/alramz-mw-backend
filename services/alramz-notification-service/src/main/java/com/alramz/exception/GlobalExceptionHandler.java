package com.alramz.exception;

import com.alramz.model.EmailSendResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.alramz.controllers")
@SuppressWarnings({"PMD.AtLeastOneConstructor", "PMD.ShortVariable"})
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<EmailSendResponse> handleValidation(final MethodArgumentNotValidException ex) {
        final String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse("400", "Bad Request", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<EmailSendResponse> handleConstraint(final ConstraintViolationException ex) {
        final String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse("400", "Bad Request", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<EmailSendResponse> handleNotReadable(final HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse("400", "Bad Request", "Malformed JSON request"));
    }

    @ExceptionHandler(EmailValidationException.class)
    public ResponseEntity<EmailSendResponse> handleEmailValidation(final EmailValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(ex.getCode(), ex.getMessage(), ex.getMessage()));
    }

    @ExceptionHandler(EmailConfigurationException.class)
    public ResponseEntity<EmailSendResponse> handleEmailConfiguration(final EmailConfigurationException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildErrorResponse("503", "Configuration Error", ex.getMessage()));
    }

    @ExceptionHandler(EmailServiceException.class)
    public ResponseEntity<EmailSendResponse> handleEmailService(final EmailServiceException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildErrorResponse(String.valueOf(ex.getStatusCode()), ex.getMessage(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<EmailSendResponse> handleGeneric(final Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse("500", "Internal Server Error", "Unhandled internal exception"));
    }

    private EmailSendResponse buildErrorResponse(final String code, final String message, final String error) {
        final EmailSendResponse response = new EmailSendResponse();
        response.setResponseCode(code);
        response.setResponseMessage(message);
        response.setError(error);
        return response;
    }
}
