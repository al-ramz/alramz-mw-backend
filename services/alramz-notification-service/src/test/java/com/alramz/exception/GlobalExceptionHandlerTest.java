package com.alramz.exception;

import com.alramz.logging.util.MDCUtil;
import com.alramz.model.EmailRequest;
import com.alramz.model.EmailSendResult;
import com.alramz.model.GenericResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        MDCUtil.putCorrelationId("cid-123");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private static void assertEnvelope(final ResponseEntity<GenericResponse> response, final int status,
                                       final String reason, final String errorCode, final String errorMsg) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        final GenericResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getResponseCode()).isEqualTo(String.valueOf(status));
        assertThat(body.getResponseMessage()).isEqualTo(reason);
        assertThat(body.getErrorCode()).isEqualTo(errorCode);
        assertThat(body.getErrorMsg()).isEqualTo(errorMsg);
        assertThat(((EmailSendResult) body.getResponse()).getCorrelationId()).isEqualTo("cid-123");
    }

    @Test
    void validationCodesMapToBadRequest() {
        for (String code : new String[]{"MGE-V-001", "MGE-V-002", "MGE-V-003", "MGE-V-004", "MGE-V-005", "MGE-V-006"}) {
            assertEnvelope(handler.handleEmailValidation(new EmailValidationException(code, "msg " + code)),
                    400, "Bad Request", code, "msg " + code);
        }
    }

    @Test
    void senderNotAllowedMapsToForbidden() {
        assertEnvelope(handler.handleEmailValidation(new EmailValidationException("MGE-V-007", "Sender mailbox is not permitted")),
                403, "Forbidden", "MGE-V-007", "Sender mailbox is not permitted");
    }

    @Test
    void sizeLimitMapsToPayloadTooLarge() {
        assertEnvelope(handler.handleEmailValidation(new EmailValidationException("MGE-V-008", "Attachments size limit exceeded")),
                413, "Payload Too Large", "MGE-V-008", "Attachments size limit exceeded");
    }

    @Test
    void accessDeniedIsForbiddenWithoutErrorCode() {
        assertEnvelope(handler.handleAccessDenied(new AccessDeniedException("no role")), 403, "Forbidden", null, null);
    }

    @Test
    void backendCodesMapToStatusesWithoutErrorDetails() {
        assertEnvelope(handler.handleEmailService(new EmailServiceException("MGE-B-001", "x")), 502, "Bad Gateway", null, null);
        assertEnvelope(handler.handleEmailService(new EmailServiceException("MGE-B-003", "x")), 502, "Bad Gateway", null, null);
        assertEnvelope(handler.handleEmailService(new EmailServiceException("MGE-B-004", "x")),
                500, "Internal Server Error", null, null);
    }

    @Test
    void throttledPassesRetryAfterThroughWhenPresent() {
        final ResponseEntity<GenericResponse> withHeader =
                handler.handleEmailService(new EmailServiceException("MGE-B-002", "x", null, "30"));
        assertEnvelope(withHeader, 429, "Too Many Requests", null, null);
        assertThat(withHeader.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");

        final ResponseEntity<GenericResponse> withoutHeader =
                handler.handleEmailService(new EmailServiceException("MGE-B-002", "x"));
        assertEnvelope(withoutHeader, 429, "Too Many Requests", null, null);
        assertThat(withoutHeader.getHeaders().containsHeader(HttpHeaders.RETRY_AFTER)).isFalse();
    }

    @Test
    void unexpectedExceptionsAreInternalServerError() {
        assertEnvelope(handler.handleGeneric(new IllegalStateException("boom")), 500, "Internal Server Error", null, null);
        assertEnvelope(handler.handleGeneric(new EmailConfigurationException("not configured")),
                500, "Internal Server Error", null, null);
    }

    @Test
    void invalidBase64InAttachmentContentIsV005WithPosition() {
        final String json = "{\"attachments\":[{\"fileName\":\"a\",\"content\":\"AAAA\"},"
                + "{\"fileName\":\"b\",\"content\":\"!!not base64!!\"}]}";
        final JacksonException cause = catchThrowableOfType(JacksonException.class,
                () -> JsonMapper.builder().build().readValue(json, EmailRequest.class));

        assertEnvelope(handler.handleNotReadable(notReadable(cause)), 400, "Bad Request", "MGE-V-005",
                "Invalid attachment at position 2: content is not valid Base64");
    }

    @Test
    void e4_invalidBase64InTemplateAttachmentIsV005() {
        final String json = "{\"attachments\":[{\"fileName\":\"a.pdf\",\"contenttype\":\"PDF\",\"content\":\"!!\"}]}";
        final JacksonException cause = catchThrowableOfType(JacksonException.class,
                () -> JsonMapper.builder().build().readValue(json, com.alramz.model.TemplatedEmailRequest.class));

        assertEnvelope(handler.handleNotReadable(notReadable(cause)), 400, "Bad Request", "MGE-V-005",
                "Invalid attachment at position 1: content is not valid Base64");
    }

    @Test
    void malformedJsonIsV006() {
        final JacksonException cause = catchThrowableOfType(JacksonException.class,
                () -> JsonMapper.builder().build().readValue("{\"to\": ", EmailRequest.class));

        assertEnvelope(handler.handleNotReadable(notReadable(cause)), 400, "Bad Request", "MGE-V-006", "Malformed JSON request");
        assertEnvelope(handler.handleNotReadable(notReadable(null)), 400, "Bad Request", "MGE-V-006", "Malformed JSON request");
    }

    @Test
    void badValueOutsideAttachmentContentIsV006() {
        final JacksonException cause = catchThrowableOfType(JacksonException.class,
                () -> JsonMapper.builder().build().readValue("{\"attachments\":{\"content\":1}}", EmailRequest.class));

        assertEnvelope(handler.handleNotReadable(notReadable(cause)), 400, "Bad Request", "MGE-V-006", "Malformed JSON request");
    }

    @Test
    void missingCorrelationIdFallsBackToUuid() {
        MDC.clear();
        final GenericResponse body = handler.handleAccessDenied(new AccessDeniedException("x")).getBody();

        assertThat(((EmailSendResult) body.getResponse()).getCorrelationId()).hasSize(36);
    }

    @Test
    void templatedEmailCallerErrorsCarryCodes() {
        for (String code : new String[]{"EML001", "EML002", "EML003", "EML004", "EML005", "EML006"}) {
            assertEnvelope(handler.handleTemplatedEmail(new TemplatedEmailException(code, "msg " + code)),
                    400, "Bad Request", code, "msg " + code);
        }
        assertEnvelope(handler.handleTemplatedEmail(new TemplatedEmailException("EML007", "Attachments size limit exceeded")),
                413, "Payload Too Large", "EML007", "Attachments size limit exceeded");
        assertEnvelope(handler.handleTemplatedEmail(new TemplatedEmailException("EML008", "Maximum number of attempts exceeded")),
                429, "Too Many Requests", "EML008", "Maximum number of attempts exceeded");
    }

    @Test
    void templatedEmailBackendErrorsHaveNoCodes() {
        assertEnvelope(handler.handleTemplatedEmail(new TemplatedEmailException("EML009", "Email service failed MGE-B-003")),
                502, "Bad Gateway", null, null);
        assertEnvelope(handler.handleTemplatedEmail(new TemplatedEmailException("EML010", "Template misconfiguration x")),
                500, "Internal Server Error", null, null);
    }

    @Test
    void smsCallerErrorsCarryCodes() {
        for (String code : new String[]{"SMS001", "SMS002", "SMS003", "SMS004", "SMS005", "SMS007"}) {
            assertEnvelope(handler.handleSms(new SmsException(code, "msg " + code)), 400, "Bad Request", code, "msg " + code);
        }
        assertEnvelope(handler.handleSms(new SmsException("SMS008", "No SMS record found")),
                404, "Not Found", "SMS008", "No SMS record found");
    }

    @Test
    void smsBackendErrorsHaveNoCodes() {
        assertEnvelope(handler.handleSms(new SmsException("SMS009", "SMS provider rejected WASELNEY 1702")),
                502, "Bad Gateway", null, null);
        assertEnvelope(handler.handleSms(new SmsException("SMS010", "SMS provider unavailable WASELNEY X")),
                503, "Service Unavailable", null, null);
        assertEnvelope(handler.handleSms(new SmsException("SMS999", "unexpected")),
                500, "Internal Server Error", null, null);
    }

    @Test
    void otpCallerErrorsCarryCodes() {
        for (String code : new String[]{"OTP001", "OTP002", "OTP003", "OTP004", "OTP005", "OTP007"}) {
            assertEnvelope(handler.handleOtp(new OtpException(code, "msg " + code)), 400, "Bad Request", code, "msg " + code);
        }
        for (String code : new String[]{"OTP006", "OTP008"}) {
            assertEnvelope(handler.handleOtp(new OtpException(code, "msg " + code)),
                    429, "Too Many Requests", code, "msg " + code);
        }
    }

    @Test
    void otpBackendErrorsHaveNoCodes() {
        assertEnvelope(handler.handleOtp(new OtpException("OTP009", "OTP delivery failed for every recipient")),
                502, "Bad Gateway", null, null);
        assertEnvelope(handler.handleOtp(new OtpException("OTP010", "OTP template misconfiguration x")),
                500, "Internal Server Error", null, null);
    }

    private static HttpMessageNotReadableException notReadable(final Throwable cause) {
        return new HttpMessageNotReadableException("JSON parse error", cause, new MockHttpInputMessage(new byte[0]));
    }
}
