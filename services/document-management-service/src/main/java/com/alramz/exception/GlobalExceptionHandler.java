package com.alramz.exception;

import com.alramz.config.DmsProperties;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import com.alramz.service.DmsRequestSummary;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tools.jackson.core.JacksonException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The only advice in this service (wealth-management-service precedent, not data-validation's unreachable
 * OnboardingExceptionHandler). Every DMS error is a GenericResponse with a DMS### code; framework input
 * failures get a fixed per-field message that never echoes the rejected value.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.alramz.controllers")
@Order(0)
public class GlobalExceptionHandler {

    static final String INVALID_REQUEST = "Invalid request";
    static final String MALFORMED_BODY = "Request body is missing or malformed";
    static final String UNSUPPORTED_CONTENT_TYPE =
            "Unsupported Content-Type: save takes multipart/form-data, search takes application/json";
    static final String MALFORMED_MULTIPART = "Request must be a valid multipart/form-data upload";
    public static final String INVALID_METADATA = "metadata must be a JSON object of metadata ID to value";

    /** Fixed client-facing messages per input name. */
    private static final Map<String, String> FIELD_MESSAGES = Map.of(
            "documentId", "documentId must be a positive integer",
            "filingCategoryId", "filingCategoryId must be a positive integer",
            "fileName", "fileName must be 1-255 characters",
            "description", "description must be at most 1000 characters",
            "page", "page must be a positive integer",
            "pageSize", "pageSize must be a positive integer",
            "overwrite", "overwrite must be true or false",
            "metadata", INVALID_METADATA,
            "file", "file part is required");

    private final DmsProperties properties;

    public GlobalExceptionHandler(DmsProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(DmsException.class)
    public ResponseEntity<GenericResponse> handleDms(DmsException ex) {
        ErrorCode code = ex.getErrorCode();
        String message = code == ErrorCode.DMS107 ? fileTooLarge() : ex.getMessage();
        return buildResponse(code.status(), code, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<GenericResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.DMS107, fileTooLarge());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<GenericResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return badRequest(ex.getRequestPartName() + " part is required");
    }

    /** A required form field of the save request (e.g. filingCategoryId) is missing. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<GenericResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequest(ex.getParameterName() + " is required");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<GenericResponse> handleMultipart(MultipartException ex) {
        return badRequest(MALFORMED_MULTIPART);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<GenericResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return badRequest(UNSUPPORTED_CONTENT_TYPE);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GenericResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest(messageFor(ex.getName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse> handleNotValid(MethodArgumentNotValidException ex) {
        return fieldError(ex.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<GenericResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        // never empty: HandlerMethodValidationException requires at least one result
        ParameterValidationResult result = ex.getParameterValidationResults().get(0);
        if (result instanceof ParameterErrors errors) {
            return fieldError(errors.getFieldErrors());
        }
        return badRequest(messageFor(inputName(result.getMethodParameter())));
    }

    /**
     * The generated DmsApi is {@code @Validated}, so parameter constraints (e.g. {@code @Min(1)} on documentId)
     * are enforced by the MethodValidationPostProcessor proxy and arrive here.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                     HandlerMethod handlerMethod) {
        ConstraintViolation<?> violation = ex.getConstraintViolations().stream()
                .min(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .orElse(null);
        if (violation == null) {
            return badRequest(INVALID_REQUEST);
        }
        String property = null;
        int paramIndex = -1;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.PROPERTY) {
                property = node.getName();
            } else if (node.getKind() == ElementKind.PARAMETER) {
                paramIndex = node.as(Path.ParameterNode.class).getParameterIndex();
            }
        }
        if (property == null && handlerMethod != null && paramIndex >= 0
                && paramIndex < handlerMethod.getMethodParameters().length) {
            property = inputName(handlerMethod.getMethodParameters()[paramIndex]);
        }
        return badRequest(messageFor(property));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        for (Throwable t = ex.getCause(); t != null; t = t.getCause()) {
            if (t instanceof JacksonException jackson) {
                String field = null;
                for (JacksonException.Reference ref : jackson.getPath()) {
                    if (ref.getPropertyName() != null) {
                        field = ref.getPropertyName();
                        break;
                    }
                }
                if (field != null && (FIELD_MESSAGES.containsKey(field) || "pageSize".equals(field))) {
                    return badRequest(messageFor(field));
                }
            }
        }
        return badRequest(MALFORMED_BODY);
    }

    // Must be explicit: otherwise the catch-all below turns @JwtSecured's 403 into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, null, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGeneric(Exception ex) {
        log.error("Unhandled DMS error (correlationId={})", MDCUtil.getCorrelationId(), ex);
        // Never leak the message or stack trace of an unexpected failure.
        return buildResponse(ErrorCode.DMS299.status(), ErrorCode.DMS299, ErrorCode.DMS299.defaultMessage());
    }

    private String fileTooLarge() {
        return ErrorCode.fileTooLargeMessage(properties.getUpload().getMaxFileSize());
    }

    private ResponseEntity<GenericResponse> fieldError(List<FieldError> fieldErrors) {
        if (fieldErrors.isEmpty()) {
            return badRequest(INVALID_REQUEST);
        }
        FieldError fe = fieldErrors.stream().min(Comparator.comparing(FieldError::getField)).orElseThrow();
        String path = fe.getField();
        String field = path.substring(Math.max(path.lastIndexOf('.'), path.lastIndexOf(']')) + 1);
        return badRequest(messageFor(field));
    }

    private String messageFor(String input) {
        if ("pageSize".equals(input)) {
            return ErrorCode.pageSizeMessage(properties.getSearch().getMaxPageSize());
        }
        return input == null ? INVALID_REQUEST : FIELD_MESSAGES.getOrDefault(input, INVALID_REQUEST);
    }

    private static String inputName(MethodParameter parameter) {
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.value().isEmpty()) {
            return pathVariable.value();
        }
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.value().isEmpty()) {
            return requestParam.value();
        }
        RequestPart requestPart = parameter.getParameterAnnotation(RequestPart.class);
        if (requestPart != null && !requestPart.value().isEmpty()) {
            return requestPart.value();
        }
        return parameter.getParameterName();
    }

    private ResponseEntity<GenericResponse> badRequest(String message) {
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCode.DMS101, message);
    }

    private ResponseEntity<GenericResponse> buildResponse(HttpStatus status, ErrorCode errorCode, String errorMsg) {
        DmsRequestSummary.current().ifPresent(summary -> summary.setDmsCode(errorCode != null ? errorCode.code() : null));
        GenericResponse body = new GenericResponse();
        body.setResponseCode(String.valueOf(status.value()));
        body.setResponseMessage(status.getReasonPhrase());
        body.setErrorCode(errorCode != null ? errorCode.code() : null);
        body.setErrorMsg(errorMsg);
        body.setCorrelationId(MDCUtil.getCorrelationIdAsUuid());
        return ResponseEntity.status(status).body(body);
    }
}
