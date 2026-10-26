package com.alramz.exception;

import com.alramz.exception.ErrorCode.Kind;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;

import java.util.Comparator;
import java.util.List;

/**
 * The only advice in this service. Every MMF error resolves to an operation-specific MMF### code: the
 * operationId is the handler method name (the generator copies it from apiSpecs.yaml).
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.alramz.controllers")
@Order(0)
public class GlobalExceptionHandler {

    private static final String INTERNAL_MSG = "Internal Server Error";
    private static final String UNAVAILABLE_MSG = "MMF data source unavailable";

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<GenericResponse> handleApplication(ApplicationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MmfNotFoundException.class)
    public ResponseEntity<GenericResponse> handleNotFound(MmfNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MmfConflictException.class)
    public ResponseEntity<GenericResponse> handleConflict(MmfConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MmfBackendException.class)
    public ResponseEntity<GenericResponse> handleBackend(MmfBackendException ex, HandlerMethod handlerMethod) {
        if (ex.getErrorCode() == null) {
            log.error("MMF backend failure", ex);
            return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.internalFor(operationOf(handlerMethod)), INTERNAL_MSG);
        }
        log.error("MMF backend failure {}: {}", ex.getErrorCode().code(), ex.getMessage(), ex.getCause());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MmfBackendUnavailableException.class)
    public ResponseEntity<GenericResponse> handleUnavailable(MmfBackendUnavailableException ex) {
        log.error("MMF book unavailable {}: {}", ex.getErrorCode().code(), ex.getMessage(), ex.getCause());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getErrorCode(), ex.getMessage());
    }

    /** Raw DB text is only logged; the body carries the operation's fixed 500/503 code and text. */
    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    public ResponseEntity<GenericResponse> handleDataAccess(Exception ex, HandlerMethod handlerMethod) {
        String op = operationOf(handlerMethod);
        if (isResourceFailure(ex)) {
            log.error("MMF data source unavailable in {}", op, ex);
            return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.unavailableFor(op), UNAVAILABLE_MSG);
        }
        log.error("MMF database error in {}", op, ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.internalFor(op), INTERNAL_MSG);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<GenericResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                  HandlerMethod handlerMethod) {
        return inputError(operationOf(handlerMethod), ex.getParameterName(), Kind.MISSING, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GenericResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                              HandlerMethod handlerMethod) {
        return inputError(operationOf(handlerMethod), ex.getName(), Kind.ANY, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse> handleNotValid(MethodArgumentNotValidException ex, HandlerMethod handlerMethod) {
        return fieldError(operationOf(handlerMethod), ex.getBindingResult().getFieldErrors(), null);
    }

    /** Spring 6.1+ method validation: @Size/@Pattern on parameters and List&lt;@Valid Item&gt; bodies. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<GenericResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        String op = ex.getMethod().getName();
        List<ParameterValidationResult> results = ex.getParameterValidationResults();
        if (results.isEmpty()) {
            return inputError(op, null, Kind.ANY, null);
        }
        ParameterValidationResult result = results.get(0);
        if (result instanceof ParameterErrors errors) {
            return fieldError(op, errors.getFieldErrors(), result.getContainerIndex());
        }
        List<MessageSourceResolvable> errors = result.getResolvableErrors();
        String constraint = errors.isEmpty() ? null : lastCode(errors.get(0).getCodes());
        return inputError(op, inputName(result.getMethodParameter()), kindOf(constraint), null);
    }

    /**
     * The generated MmfApi is {@code @Validated}, so Spring MVC skips its built-in method validation and the
     * MethodValidationPostProcessor proxy raises this instead (for @Size/@Pattern/@Min and List&lt;@Valid Item&gt;).
     * Violations are a Set, so the first one is chosen deterministically: lowest parameter, then
     * parameter-level before element-level, then lowest item index, then path.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponse> handleConstraintViolation(ConstraintViolationException ex, HandlerMethod handlerMethod) {
        String op = operationOf(handlerMethod);
        ConstraintViolation<?> violation = ex.getConstraintViolations().stream()
                .min(Comparator.<ConstraintViolation<?>>comparingInt(v -> parameterIndex(v.getPropertyPath()))
                        .thenComparingInt(v -> itemIndex(v.getPropertyPath()))
                        .thenComparing(v -> v.getPropertyPath().toString()))
                .orElse(null);
        if (violation == null) {
            return inputError(op, null, Kind.ANY, null);
        }
        String property = null;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.PROPERTY) {
                property = node.getName();
            }
        }
        Kind kind = kindOf(violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
        if (property != null) {
            int item = itemIndex(violation.getPropertyPath());
            return inputError(op, property, kind, item >= 0 ? item : null);
        }
        int paramIndex = parameterIndex(violation.getPropertyPath());
        String input = handlerMethod != null && paramIndex >= 0 && paramIndex < handlerMethod.getMethodParameters().length
                ? inputName(handlerMethod.getMethodParameters()[paramIndex]) : null;
        return inputError(op, input, kind, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse> handleNotReadable(HttpMessageNotReadableException ex, HandlerMethod handlerMethod) {
        String op = operationOf(handlerMethod);
        for (Throwable t = ex.getCause(); t != null; t = t.getCause()) {
            if (t instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
                String field = null;
                Integer index = null;
                for (JacksonException.Reference ref : jackson.getPath()) {
                    if (ref.getPropertyName() != null) {
                        field = ref.getPropertyName();
                    } else if (ref.getIndex() >= 0) {
                        index = ref.getIndex();
                    }
                }
                if (field != null) {
                    return inputError(op, field, Kind.ANY, index);
                }
            }
        }
        return inputError(op, ErrorCode.BODY, Kind.MISSING, null);
    }

    // Must be explicit: otherwise the catch-all below turns @JwtSecured's 403 into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, null, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGeneric(Exception ex, HandlerMethod handlerMethod) {
        String op = operationOf(handlerMethod);
        log.error("Unhandled error in {}", op, ex);
        // Never leak the message or stack trace of an unexpected failure.
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.internalFor(op), INTERNAL_MSG);
    }

    /** True when the failure (or any cause) means the book could not be reached, i.e. 503 rather than 500. */
    public static boolean isResourceFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof DataAccessResourceFailureException || t instanceof TransientDataAccessResourceException) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<GenericResponse> fieldError(String op, List<FieldError> fieldErrors, Integer itemIndex) {
        if (fieldErrors.isEmpty()) {
            return inputError(op, ErrorCode.BODY, Kind.ANY, null);
        }
        FieldError fe = fieldErrors.get(0);
        String path = fe.getField();
        String field = path.substring(Math.max(path.lastIndexOf('.'), path.lastIndexOf(']')) + 1);
        return inputError(op, field, kindOf(fe.getCode()), itemIndex);
    }

    private ResponseEntity<GenericResponse> inputError(String op, String input, Kind kind, Integer itemIndex) {
        String message = ErrorCode.inputMessage(op, input, kind);
        if (itemIndex != null) {
            message = "Item " + (itemIndex + 1) + ": " + message;
        }
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCode.forInput(op, input, kind), message);
    }

    private static int parameterIndex(Path path) {
        for (Path.Node node : path) {
            if (node.getKind() == ElementKind.PARAMETER) {
                return node.as(Path.ParameterNode.class).getParameterIndex();
            }
        }
        return -1;
    }

    /** Index of the list element the violation sits in, or -1 for a parameter-level violation (e.g. @Size). */
    private static int itemIndex(Path path) {
        for (Path.Node node : path) {
            if (node.getIndex() != null) {
                return node.getIndex();
            }
        }
        return -1;
    }

    private static Kind kindOf(String constraint) {
        if ("NotNull".equals(constraint)) {
            return Kind.MISSING;
        }
        return "Size".equals(constraint) ? Kind.SIZE : Kind.ANY;
    }

    private static String lastCode(String[] codes) {
        return codes == null || codes.length == 0 ? null : codes[codes.length - 1];
    }

    private static String inputName(MethodParameter parameter) {
        if (parameter.hasParameterAnnotation(RequestBody.class)) {
            return ErrorCode.BODY;
        }
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.value().isEmpty()) {
            return requestParam.value();
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.value().isEmpty()) {
            return pathVariable.value();
        }
        return parameter.getParameterName();
    }

    private static String operationOf(HandlerMethod handlerMethod) {
        return handlerMethod == null ? null : handlerMethod.getMethod().getName();
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
