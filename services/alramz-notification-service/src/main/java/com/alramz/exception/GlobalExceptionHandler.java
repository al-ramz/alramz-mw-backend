package com.alramz.exception;

import com.alramz.logging.util.MDCUtil;
import com.alramz.model.EmailSendResult;
import com.alramz.model.GenericResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.UUID;

/**
 * The only advice in this service. Every response is a GenericResponse whose response is an EmailSendResult.
 * Backend failures (MGE-B-00x) are already logged by EmailServiceImpl and carry no errorCode/errorMsg.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.alramz.controllers")
@Order(0)
@SuppressWarnings({"PMD.AtLeastOneConstructor", "PMD.ShortVariable"})
public class GlobalExceptionHandler {

    // PAYLOAD_TOO_LARGE (not CONTENT_TOO_LARGE) keeps the contract's "Payload Too Large" reason phrase.
    @SuppressWarnings("deprecation")
    @ExceptionHandler(EmailValidationException.class)
    public ResponseEntity<GenericResponse> handleEmailValidation(final EmailValidationException ex) {
        final HttpStatus status = switch (ex.getCode()) {
            case "MGE-V-007" -> HttpStatus.FORBIDDEN;
            case "MGE-V-008" -> HttpStatus.PAYLOAD_TOO_LARGE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return buildResponse(status, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(EmailServiceException.class)
    public ResponseEntity<GenericResponse> handleEmailService(final EmailServiceException ex) {
        final HttpStatus status = switch (ex.getCode()) {
            case "MGE-B-002" -> HttpStatus.TOO_MANY_REQUESTS;
            case "MGE-B-004" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_GATEWAY;
        };
        final ResponseEntity<GenericResponse> response = buildResponse(status, null, null);
        if (ex.getRetryAfter() == null) {
            return response;
        }
        return ResponseEntity.status(status).header(HttpHeaders.RETRY_AFTER, ex.getRetryAfter()).body(response.getBody());
    }

    /** EML001-008 carry their code; EML009 (gateway) and EML010 (misconfiguration) are logged and carry none. */
    @SuppressWarnings("deprecation")
    @ExceptionHandler(TemplatedEmailException.class)
    public ResponseEntity<GenericResponse> handleTemplatedEmail(final TemplatedEmailException ex) {
        final HttpStatus status = switch (ex.getCode()) {
            case "EML001", "EML002", "EML003", "EML004", "EML005", "EML006" -> HttpStatus.BAD_REQUEST;
            case "EML007" -> HttpStatus.PAYLOAD_TOO_LARGE;
            case "EML008" -> HttpStatus.TOO_MANY_REQUESTS;
            case "EML009" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return codedOrLogged(status, ex.getCode(), ex.getMessage(), ex);
    }

    /** SMS001-005/007 (400) and SMS008 (404) carry their code; provider/unexpected failures are logged and carry none. */
    @ExceptionHandler(SmsException.class)
    public ResponseEntity<GenericResponse> handleSms(final SmsException ex) {
        final HttpStatus status = switch (ex.getCode()) {
            case "SMS001", "SMS002", "SMS003", "SMS004", "SMS005", "SMS007" -> HttpStatus.BAD_REQUEST;
            case "SMS008" -> HttpStatus.NOT_FOUND;
            case "SMS009" -> HttpStatus.BAD_GATEWAY;
            case "SMS010" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return codedOrLogged(status, ex.getCode(), ex.getMessage(), ex);
    }

    /** OTP001-008 carry their code; OTP009 (every delivery failed) and OTP010 (misconfiguration) are logged and carry none. */
    @ExceptionHandler(OtpException.class)
    public ResponseEntity<GenericResponse> handleOtp(final OtpException ex) {
        final HttpStatus status = switch (ex.getCode()) {
            case "OTP001", "OTP002", "OTP003", "OTP004", "OTP005", "OTP007" -> HttpStatus.BAD_REQUEST;
            case "OTP006", "OTP008" -> HttpStatus.TOO_MANY_REQUESTS;
            case "OTP009" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return codedOrLogged(status, ex.getCode(), ex.getMessage(), ex);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse> handleNotReadable(final HttpMessageNotReadableException ex) {
        for (Throwable t = ex.getCause(); t != null; t = t.getCause()) {
            if (t instanceof JacksonException jackson) {
                final Integer position = attachmentContentPosition(jackson.getPath());
                if (position != null) {
                    return buildResponse(HttpStatus.BAD_REQUEST, "MGE-V-005",
                            "Invalid attachment at position " + position + ": content is not valid Base64");
                }
            }
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "MGE-V-006", "Malformed JSON request");
    }

    // Must be explicit: otherwise the catch-all below turns @JwtSecured's 403 into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse> handleAccessDenied(final AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGeneric(final Exception ex) {
        log.error("MGE-B-004 Unhandled email gateway error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, null, null);
    }

    /** 4xx: the code and message go to the caller. 5xx: logged here (messages are built without caller data). */
    private static ResponseEntity<GenericResponse> codedOrLogged(final HttpStatus status, final String code,
                                                                 final String message, final Exception ex) {
        if (status.is5xxServerError()) {
            log.error("{} {} correlationId={}", code, message, MDCUtil.getCorrelationId(),
                    HttpStatus.INTERNAL_SERVER_ERROR == status ? ex : null);
            return buildResponse(status, null, null);
        }
        return buildResponse(status, code, message);
    }

    /** 1-based attachment position when the path is attachments[n].content, else null. */
    private static Integer attachmentContentPosition(final List<JacksonException.Reference> path) {
        final int size = path.size();
        if (size < 3) {
            return null;
        }
        final JacksonException.Reference list = path.get(size - 3);
        final JacksonException.Reference item = path.get(size - 2);
        final JacksonException.Reference field = path.get(size - 1);
        if ("attachments".equals(list.getPropertyName()) && item.getIndex() >= 0
                && "content".equals(field.getPropertyName())) {
            return item.getIndex() + 1;
        }
        return null;
    }

    private static ResponseEntity<GenericResponse> buildResponse(final HttpStatus status, final String errorCode,
                                                                 final String errorMsg) {
        final String correlationId = MDCUtil.getCorrelationId();
        final GenericResponse body = new GenericResponse()
                .responseCode(String.valueOf(status.value()))
                .responseMessage(status.getReasonPhrase())
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .response(new EmailSendResult().correlationId(
                        correlationId != null ? correlationId : UUID.randomUUID().toString()));
        return ResponseEntity.status(status).body(body);
    }
}
