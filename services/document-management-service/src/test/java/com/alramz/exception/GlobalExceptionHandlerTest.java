package com.alramz.exception;

import com.alramz.config.DmsProperties;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import com.alramz.service.DmsRequestSummary;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.MethodValidationAdapter;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Direct instantiation (utility/wealth precedent): every mapping, the envelope shape, no value echo. */
class GlobalExceptionHandlerTest {

    private static final String CORRELATION_ID = "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33";

    private GlobalExceptionHandler handler;
    private DmsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DmsProperties();
        handler = new GlobalExceptionHandler(properties);
        MDCUtil.putCorrelationId(CORRELATION_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void dmsException_usesItsCodeStatusAndMessage() {
        ResponseEntity<GenericResponse> response = handler.handleDms(
                new DmsException(ErrorCode.DMS103, "Metadata 99999 is not defined on filing category 42"));

        assertError(response, HttpStatus.BAD_REQUEST, "DMS103", "Metadata 99999 is not defined on filing category 42");
    }

    @Test
    void dmsException_dms107_isRenderedFromConfig() {
        properties.getUpload().setMaxFileSize(DataSize.ofMegabytes(5));

        assertError(handler.handleDms(new DmsException(ErrorCode.DMS107)), HttpStatus.PAYLOAD_TOO_LARGE, "DMS107",
                "File exceeds the maximum size of 5 MB");
    }

    @Test
    void maxUploadSize_is413Dms107() {
        assertError(handler.handleMaxUploadSize(new MaxUploadSizeExceededException(10)), HttpStatus.PAYLOAD_TOO_LARGE,
                "DMS107", "File exceeds the maximum size of 10 MB");
    }

    @Test
    void missingParts_areDms101() {
        assertError(handler.handleMissingPart(new MissingServletRequestPartException("file")), HttpStatus.BAD_REQUEST,
                "DMS101", "file part is required");
        assertError(handler.handleMissingParameter(
                        new org.springframework.web.bind.MissingServletRequestParameterException("filingCategoryId", "Long")),
                HttpStatus.BAD_REQUEST, "DMS101", "filingCategoryId is required");
    }

    @Test
    void multipartAndMediaType_areDms101() {
        assertError(handler.handleMultipart(new MultipartException("boom")), HttpStatus.BAD_REQUEST, "DMS101",
                GlobalExceptionHandler.MALFORMED_MULTIPART);
        assertError(handler.handleMediaType(new HttpMediaTypeNotSupportedException("text/plain")), HttpStatus.BAD_REQUEST,
                "DMS101", "Unsupported Content-Type: save takes multipart/form-data, search takes application/json");
    }

    @Test
    void typeMismatch_neverEchoesTheValue() throws Exception {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("abc", Long.class,
                "documentId", new MethodParameter(Probe.method(), 0), new NumberFormatException("abc"));

        ResponseEntity<GenericResponse> response = handler.handleTypeMismatch(ex);

        assertError(response, HttpStatus.BAD_REQUEST, "DMS101", "documentId must be a positive integer");
        assertThat(response.getBody().getErrorMsg()).doesNotContain("abc");
    }

    @Test
    void notValid_usesTheFieldMessage() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
        result.addError(new FieldError("request", "fileName", "secret-name.pdf", false, null, null, "too long"));
        result.addError(new FieldError("request", "filingCategoryId", 0, false, null, null, "min"));

        ResponseEntity<GenericResponse> response = handler.handleNotValid(
                new MethodArgumentNotValidException(new MethodParameter(Probe.method(), 0), result));

        assertError(response, HttpStatus.BAD_REQUEST, "DMS101", "fileName must be 1-255 characters");
        assertThat(response.getBody().getErrorMsg()).doesNotContain("secret-name");
    }

    @Test
    void notValid_withoutFieldErrors_isGeneric() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");

        assertError(handler.handleNotValid(new MethodArgumentNotValidException(new MethodParameter(Probe.method(), 0), result)),
                HttpStatus.BAD_REQUEST, "DMS101", "Invalid request");
    }

    @Test
    void notValid_pageSize_namesTheConfiguredMaximum() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "dmsSearchRequest");
        result.addError(new FieldError("dmsSearchRequest", "pageSize", "min"));

        assertError(handler.handleNotValid(new MethodArgumentNotValidException(new MethodParameter(Probe.method(), 0), result)),
                HttpStatus.BAD_REQUEST, "DMS101", "pageSize must be between 1 and 100");
    }

    @Test
    void handlerMethodValidation_namesTheParameter() throws Exception {
        MethodValidationResult result = new MethodValidationAdapter()
                .validateArguments(new Probe(), Probe.method(), null, new Object[]{0L, null, null}, new Class<?>[0]);

        assertError(handler.handleMethodValidation(new HandlerMethodValidationException(result)),
                HttpStatus.BAD_REQUEST, "DMS101", "documentId must be a positive integer");
    }

    @Test
    void handlerMethodValidation_ofABeanParameter_usesTheFieldMessage() throws Exception {
        Method method = BeanProbe.class.getDeclaredMethod("save", Bean.class);
        MethodValidationResult result = new MethodValidationAdapter()
                .validateArguments(new BeanProbe(), method, null, new Object[]{new Bean(0L)}, new Class<?>[0]);

        assertError(handler.handleMethodValidation(new HandlerMethodValidationException(result)),
                HttpStatus.BAD_REQUEST, "DMS101", "filingCategoryId must be a positive integer");
    }

    @Test
    void constraintViolation_onPathVariable_namesTheParameter() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Probe probe = new Probe();
        Set<ConstraintViolation<Probe>> violations = validator.forExecutables()
                .validateParameters(probe, Probe.method(), new Object[]{0L, null, null});

        ResponseEntity<GenericResponse> response = handler.handleConstraintViolation(
                new ConstraintViolationException(violations), new HandlerMethod(probe, Probe.method()));

        assertError(response, HttpStatus.BAD_REQUEST, "DMS101", "documentId must be a positive integer");
    }

    @Test
    void constraintViolation_onRequestParamAndPart_namesThem() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Probe probe = new Probe();
        HandlerMethod handlerMethod = new HandlerMethod(probe, Probe.method());

        Set<ConstraintViolation<Probe>> page = validator.forExecutables()
                .validateParameters(probe, Probe.method(), new Object[]{1L, 0, null});
        assertError(handler.handleConstraintViolation(new ConstraintViolationException(page), handlerMethod),
                HttpStatus.BAD_REQUEST, "DMS101", "page must be a positive integer");

        Set<ConstraintViolation<Probe>> part = validator.forExecutables()
                .validateParameters(probe, Probe.method(), new Object[]{1L, 1, 0});
        assertError(handler.handleConstraintViolation(new ConstraintViolationException(part), handlerMethod),
                HttpStatus.BAD_REQUEST, "DMS101", "Invalid request");
    }

    @Test
    void constraintViolation_onBeanProperty_namesTheProperty() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Bean>> violations = validator.validate(new Bean(0L));

        assertError(handler.handleConstraintViolation(new ConstraintViolationException(violations), null),
                HttpStatus.BAD_REQUEST, "DMS101", "filingCategoryId must be a positive integer");
    }

    @Test
    void constraintViolation_empty_isGeneric() {
        assertError(handler.handleConstraintViolation(new ConstraintViolationException(Set.of()), null),
                HttpStatus.BAD_REQUEST, "DMS101", "Invalid request");
    }

    @Test
    void notReadable_withKnownField_usesItsMessageWithoutTheValue() {
        JacksonException jackson = catchThrowableOfType(JacksonException.class,
                () -> JsonMapper.builder().build().readValue("{\"filingCategoryId\":\"FIT-100234\"}", Bean.class));

        ResponseEntity<GenericResponse> response = handler.handleNotReadable(
                new HttpMessageNotReadableException("bad", jackson, new MockHttpInputMessage(new byte[0])));

        assertError(response, HttpStatus.BAD_REQUEST, "DMS101", "filingCategoryId must be a positive integer");
        assertThat(response.getBody().getErrorMsg()).doesNotContain("FIT-100234");
    }

    @Test
    void notReadable_malformed_isGeneric() {
        JacksonException jackson = catchThrowableOfType(JacksonException.class,
                () -> JsonMapper.builder().build().readValue("{not json", Bean.class));

        assertError(handler.handleNotReadable(new HttpMessageNotReadableException("bad", jackson,
                new MockHttpInputMessage(new byte[0]))), HttpStatus.BAD_REQUEST, "DMS101", GlobalExceptionHandler.MALFORMED_BODY);
        assertError(handler.handleNotReadable(new HttpMessageNotReadableException("bad",
                new MockHttpInputMessage(new byte[0]))), HttpStatus.BAD_REQUEST, "DMS101", GlobalExceptionHandler.MALFORMED_BODY);
    }

    @Test
    void accessDenied_is403WithNullCode() {
        ResponseEntity<GenericResponse> response = handler.handleAccessDenied(new AccessDeniedException("Insufficient role"));

        assertError(response, HttpStatus.FORBIDDEN, null, "Access denied");
    }

    @Test
    void unexpected_is500Dms299WithoutLeakingTheMessage() {
        ResponseEntity<GenericResponse> response = handler.handleGeneric(new IllegalStateException("db password=hunter2"));

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "DMS299", "Internal Server Error");
    }

    @Test
    void buildResponse_recordsTheCodeForTheSummaryLine() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        DmsRequestSummary summary = new DmsRequestSummary("saveDmsDocument", System.nanoTime());
        request.setAttribute(DmsRequestSummary.ATTRIBUTE, summary);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        handler.handleDms(new DmsException(ErrorCode.DMS104));

        assertThat(summary.getDmsCode()).isEqualTo("DMS104");
    }

    @Test
    void correlationId_isFreshWhenAbsent() {
        MDC.clear();

        ResponseEntity<GenericResponse> response = handler.handleDms(new DmsException(ErrorCode.DMS109));

        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }

    private static void assertError(ResponseEntity<GenericResponse> response, HttpStatus status, String code, String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        GenericResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getResponseCode()).isEqualTo(String.valueOf(status.value()));
        assertThat(body.getResponseMessage()).isEqualTo(status.getReasonPhrase());
        assertThat(body.getErrorCode()).isEqualTo(code);
        assertThat(body.getErrorMsg()).isEqualTo(message);
        assertThat(body.getResponse()).isNull();
        assertThat(body.getCorrelationId()).isNotNull();
        if (MDCUtil.getCorrelationId() != null) {
            assertThat(body.getCorrelationId()).hasToString(CORRELATION_ID);
        }
    }

    /** Stand-in handler method with the annotations the advice reads. */
    static class Probe {
        static Method method() throws NoSuchMethodException {
            return Probe.class.getDeclaredMethod("download", Long.class, Integer.class, Integer.class);
        }

        @SuppressWarnings("unused")
        void download(@PathVariable("documentId") @Min(1) Long documentId,
                      @RequestParam("page") @Min(1) Integer page,
                      @RequestPart("other") @Min(1) Integer other) {
            // probe only
        }
    }

    record Bean(@Min(1) Long filingCategoryId) {
    }

    static class BeanProbe {
        @SuppressWarnings("unused")
        void save(@jakarta.validation.Valid Bean request) {
            // probe only
        }
    }
}
