package com.alramz.exception;

import com.alramz.controllers.DfmInvestorController;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Error envelope for {@link DfmInvestorController} only. {@code @Order(-1)} is lower than GlobalExceptionHandler's
 * {@code @Order(0)}, so this narrower advice actually wins (exception-handling.md §4 Step 4; unlike the unreachable
 * OnboardingExceptionHandler). Bodies carry fixed texts for 5xx and never an exception class, stack trace or DFM body.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = DfmInvestorController.class)
@Order(-1)
public class DfmInvestorExceptionHandler {

    private record FieldProblem(String path, String message) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
        return invalid(ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldProblem(fe.getField(), fe.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return invalid(ex.getConstraintViolations().stream()
                .map(v -> new FieldProblem(beanPath(v), v.getMessage()))
                .toList());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<GenericResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<FieldProblem> problems = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String path = error instanceof FieldError fe ? fe.getField() : result.getMethodParameter().getParameterName();
                problems.add(new FieldProblem(path, error.getDefaultMessage()));
            }
        });
        return invalid(problems);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, DfmInvestorErrorCode.DFN003.code(), "Malformed or invalid JSON request");
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<GenericResponse> handleApplication(ApplicationException ex) {
        String code = ex.getServiceErrorResponseCode() != null ? ex.getServiceErrorResponseCode() : DfmInvestorErrorCode.DFN003.code();
        return body(HttpStatus.BAD_REQUEST, code, ex.getMessage());
    }

    @ExceptionHandler(DfmRejectedException.class)
    public ResponseEntity<GenericResponse> handleRejected(DfmRejectedException ex) {
        HttpStatus status = ex.getCode() == DfmInvestorErrorCode.DFN007 ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return body(status, ex.getCode().code(), ex.getMessage());
    }

    @ExceptionHandler(DfmUnavailableException.class)
    public ResponseEntity<GenericResponse> handleUnavailable(DfmUnavailableException ex) {
        log.warn("DFM unavailable: errorCode={}", ex.getCode(), ex);
        return switch (ex.getCode()) {
            case DFN011 -> body(HttpStatus.GATEWAY_TIMEOUT, ex.getCode().code(),
                    "DFM did not respond in time; retry later with the same requestId");
            case DFN014 -> body(HttpStatus.SERVICE_UNAVAILABLE, ex.getCode().code(),
                    "DFM is temporarily unavailable; retry later with the same requestId");
            default -> body(HttpStatus.BAD_GATEWAY, DfmInvestorErrorCode.DFN010.code(),
                    "DFM returned an invalid or failed response");
        };
    }

    @ExceptionHandler(DfmConfigurationException.class)
    public ResponseEntity<GenericResponse> handleConfiguration(DfmConfigurationException ex) {
        log.error("DFM integration is not configured: {}", ex.getMessage());
        return body(HttpStatus.INTERNAL_SERVER_ERROR, DfmInvestorErrorCode.DFN012.code(), "DFM integration is not configured");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse> handleAccessDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, null, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            // Framework-level request errors (405/406/415...) keep their status instead of becoming a 500.
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return body(status, DfmInvestorErrorCode.DFN003.code(), status.getReasonPhrase());
        }
        log.error("Unexpected error registering a DFM investor", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, DfmInvestorErrorCode.DFN013.code(), "Internal Server Error");
    }

    private static ResponseEntity<GenericResponse> invalid(List<FieldProblem> problems) {
        List<FieldProblem> sorted = problems.stream().sorted(Comparator.comparing(FieldProblem::path)).toList();
        DfmInvestorErrorCode code = DfmInvestorErrorCode.forFields(sorted.stream().map(FieldProblem::path).toList());
        String message = sorted.stream()
                .map(p -> p.path() + ": " + (p.message() != null ? p.message() : "invalid"))
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, code.code(), message);
    }

    /** Drops the leading method and parameter nodes of a method-validation path, leaving e.g. {@code address.country}. */
    private static String beanPath(ConstraintViolation<?> violation) {
        StringBuilder path = new StringBuilder();
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.METHOD || node.getKind() == ElementKind.PARAMETER) {
                continue;
            }
            if (node.isInIterable()) {
                // Hibernate puts the index on the node after the collection: countries[0].country
                path.append('[').append(node.getIndex() != null ? node.getIndex() : node.getKey()).append(']');
            }
            if (node.getName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(node.getName());
            }
        }
        return path.toString();
    }

    private static ResponseEntity<GenericResponse> body(HttpStatus status, String errorCode, String errorMsg) {
        GenericResponse response = new GenericResponse(String.valueOf(status.value()), status.getReasonPhrase(), null);
        response.setErrorCode(errorCode);
        response.setErrorMsg(errorMsg);
        response.setCorrelationId(MDCUtil.getCorrelationIdAsUuid());
        return ResponseEntity.status(status).body(response);
    }
}
