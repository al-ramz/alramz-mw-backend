package com.alramz.exception;

import com.alramz.controllers.DfmInvestorController;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.DfmAddress;
import com.alramz.model.DfmInvestorRegistrationRequest;
import com.alramz.model.GenericResponse;
import com.alramz.service.DfmInvestorService;
import com.alramz.service.impl.DfmInvestorServiceImplTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DfmInvestorExceptionHandlerTest {

    private static final String CORRELATION_ID = "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33";
    private static final String SENTINEL = "SENTINEL-securitykey-test-key-stack";

    private final DfmInvestorExceptionHandler handler = new DfmInvestorExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    private static void assertEnvelope(ResponseEntity<GenericResponse> response, HttpStatus status, String code, String msg) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        GenericResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getResponseCode()).isEqualTo(String.valueOf(status.value()));
        assertThat(body.getResponseMessage()).isEqualTo(status.getReasonPhrase());
        assertThat(body.getErrorCode()).isEqualTo(code);
        assertThat(body.getErrorMsg()).isEqualTo(msg);
        assertThat(body.getResponse()).isNull();
        assertThat(body.getCorrelationId()).isNotNull();
    }

    private static void assertNoLeak(ResponseEntity<GenericResponse> response) throws Exception {
        String json = new ObjectMapper().writeValueAsString(response.getBody());
        assertThat(json).doesNotContain(SENTINEL).doesNotContain("Exception").doesNotContain("at com.");
    }

    private static MethodArgumentNotValidException invalid(FieldError... errors) throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new DfmInvestorRegistrationRequest(), "dfmInvestorRegistrationRequest");
        for (FieldError error : errors) {
            result.addError(error);
        }
        Method method = DfmInvestorController.class.getMethod("registerDfmInvestor", DfmInvestorRegistrationRequest.class);
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), result);
    }

    private static FieldError error(String field, String message) {
        return new FieldError("dfmInvestorRegistrationRequest", field, message);
    }

    // ---------- Bean Validation ----------

    @Test
    void beanValidation_singleFieldCodes() throws Exception {
        assertEnvelope(handler.handleInvalidBody(invalid(error("address.country", "must not be null"))),
                HttpStatus.BAD_REQUEST, "DFN001", "address.country: must not be null");
        assertEnvelope(handler.handleInvalidBody(invalid(error("address.city", "size must be between 1 and 25"))),
                HttpStatus.BAD_REQUEST, "DFN002", "address.city: size must be between 1 and 25");
        assertEnvelope(handler.handleInvalidBody(invalid(error("mobile", "must match"))),
                HttpStatus.BAD_REQUEST, "DFN004", "mobile: must match");
        assertEnvelope(handler.handleInvalidBody(invalid(error("address.line", "size must be between 0 and 40"))),
                HttpStatus.BAD_REQUEST, "DFN005", "address.line: size must be between 0 and 40");
        assertEnvelope(handler.handleInvalidBody(invalid(error("address.phone", "must match"))),
                HttpStatus.BAD_REQUEST, "DFN003", "address.phone: must match");
    }

    @Test
    void beanValidation_multipleErrors_shouldSortByPathAndPickByPrecedence() throws Exception {
        ResponseEntity<GenericResponse> response = handler.handleInvalidBody(invalid(
                error("mobile", "bad mobile"),
                error("firstName", "must not be null"),
                error("address.line", "too long"),
                error("address.city", "too long")));

        assertEnvelope(response, HttpStatus.BAD_REQUEST, "DFN002",
                "address.city: too long; address.line: too long; firstName: must not be null; mobile: bad mobile");
    }

    @Test
    void beanValidation_withoutDefaultMessage_shouldSayInvalid() throws Exception {
        assertEnvelope(handler.handleInvalidBody(invalid(error("email", null))), HttpStatus.BAD_REQUEST, "DFN003", "email: invalid");
    }

    @Test
    void beanValidation_crsCountry_shouldNotBeMistakenForAddressCountry() throws Exception {
        assertThat(handler.handleInvalidBody(invalid(error("crs.countries[0].country", "must match"))).getBody().getErrorCode())
                .isEqualTo("DFN003");
    }

    @Test
    void constraintViolation_fromMethodValidation_shouldStripMethodAndArgumentPrefix() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        DfmInvestorRegistrationRequest request = DfmInvestorServiceImplTest.validRequest();
        request.getAddress().country("uae");
        Method method = com.alramz.api.DfmInvestorsApi.class.getMethod("registerDfmInvestor", DfmInvestorRegistrationRequest.class);
        Set<ConstraintViolation<Object>> violations = validator.forExecutables().validateParameters(
                new DfmInvestorController(mock(DfmInvestorService.class)), method, new Object[]{request});

        ResponseEntity<GenericResponse> response = handler.handleConstraintViolation(new ConstraintViolationException(violations));

        assertThat(response.getBody().getErrorCode()).isEqualTo("DFN001");
        assertThat(response.getBody().getErrorMsg()).startsWith("address.country: ");
    }

    @Test
    void constraintViolation_onBean_shouldKeepThePath() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<DfmAddress>> violations = validator.validate(new DfmAddress().country("AE").city(""));

        ResponseEntity<GenericResponse> response = handler.handleConstraintViolation(new ConstraintViolationException(violations));

        assertEnvelope(response, HttpStatus.BAD_REQUEST, "DFN003", response.getBody().getErrorMsg());
        assertThat(response.getBody().getErrorMsg()).startsWith("city: ");
    }

    @Test
    void constraintViolation_inList_shouldRenderTheIndexedPath() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        DfmInvestorRegistrationRequest request = DfmInvestorServiceImplTest.validRequest()
                .crs(new com.alramz.model.DfmCrs().countries(List.of(new com.alramz.model.DfmCrsCountry().country("x").hasTin(true))));

        ResponseEntity<GenericResponse> response = handler.handleConstraintViolation(new ConstraintViolationException(validator.validate(request)));

        assertThat(response.getBody().getErrorCode()).isEqualTo("DFN003");
        assertThat(response.getBody().getErrorMsg()).startsWith("crs.countries[0].country: ");
    }

    @Test
    void handlerMethodValidation_shouldUseFieldErrorsAndParameterNames() {
        ParameterErrors parameterErrors = mock(ParameterErrors.class);
        FieldError fieldError = error("mobile", "bad");
        when(parameterErrors.getResolvableErrors()).thenReturn(List.of(fieldError));
        ParameterValidationResult other = mock(ParameterValidationResult.class);
        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.getParameterName()).thenReturn("dfmInvestorRegistrationRequest");
        when(other.getMethodParameter()).thenReturn(parameter);
        when(other.getResolvableErrors()).thenReturn(List.of(new org.springframework.context.support.DefaultMessageSourceResolvable(
                new String[]{"NotNull"}, "must not be null")));
        MethodValidationResult result = mock(MethodValidationResult.class);
        when(result.getParameterValidationResults()).thenReturn(List.of(parameterErrors, other));
        HandlerMethodValidationException ex = new HandlerMethodValidationException(result);

        ResponseEntity<GenericResponse> response = handler.handleHandlerMethodValidation(ex);

        assertEnvelope(response, HttpStatus.BAD_REQUEST, "DFN004",
                "dfmInvestorRegistrationRequest: must not be null; mobile: bad");
    }

    @Test
    void unreadableJson_shouldBeDfn003() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(SENTINEL, new MockHttpInputMessage(new byte[0]));

        assertEnvelope(handler.handleUnreadable(ex), HttpStatus.BAD_REQUEST, "DFN003", "Malformed or invalid JSON request");
    }

    // ---------- typed exceptions ----------

    @Test
    void applicationException_shouldUseItsCodeAndMessage() {
        assertEnvelope(handler.handleApplication(new ApplicationException("address.country",
                        "address.country must be an ISO 3166-1 alpha-2 code", "DFN001")),
                HttpStatus.BAD_REQUEST, "DFN001", "address.country must be an ISO 3166-1 alpha-2 code");
    }

    @Test
    void applicationExceptionWithoutCode_shouldDefaultToDfn003() {
        assertEnvelope(handler.handleApplication(new ApplicationException("x", "x is wrong")),
                HttpStatus.BAD_REQUEST, "DFN003", "x is wrong");
    }

    @Test
    void rejected_shouldEchoDfmMessage() {
        assertEnvelope(handler.handleRejected(new DfmRejectedException(DfmInvestorErrorCode.DFN006, "Request id should be a valid GUID.")),
                HttpStatus.BAD_REQUEST, "DFN006", "Request id should be a valid GUID.");
        assertEnvelope(handler.handleRejected(new DfmRejectedException(DfmInvestorErrorCode.DFN007, "still busy")),
                HttpStatus.CONFLICT, "DFN007", "still busy");
    }

    @Test
    void unavailable_shouldUseFixedTextsAndNeverLeak() throws Exception {
        RuntimeException cause = new IllegalStateException(SENTINEL);
        ResponseEntity<GenericResponse> badGateway = handler.handleUnavailable(new DfmUnavailableException(DfmInvestorErrorCode.DFN010, SENTINEL, cause));
        ResponseEntity<GenericResponse> timeout = handler.handleUnavailable(new DfmUnavailableException(DfmInvestorErrorCode.DFN011, SENTINEL, cause));
        ResponseEntity<GenericResponse> open = handler.handleUnavailable(new DfmUnavailableException(DfmInvestorErrorCode.DFN014, SENTINEL, cause));

        assertEnvelope(badGateway, HttpStatus.BAD_GATEWAY, "DFN010", "DFM returned an invalid or failed response");
        assertEnvelope(timeout, HttpStatus.GATEWAY_TIMEOUT, "DFN011", "DFM did not respond in time; retry later with the same requestId");
        assertEnvelope(open, HttpStatus.SERVICE_UNAVAILABLE, "DFN014", "DFM is temporarily unavailable; retry later with the same requestId");
        assertNoLeak(badGateway);
        assertNoLeak(timeout);
        assertNoLeak(open);
    }

    @Test
    void configuration_shouldBe500Dfn012WithoutLeak() throws Exception {
        ResponseEntity<GenericResponse> response = handler.handleConfiguration(new DfmConfigurationException(SENTINEL));

        assertEnvelope(response, HttpStatus.INTERNAL_SERVER_ERROR, "DFN012", "DFM integration is not configured");
        assertNoLeak(response);
    }

    @Test
    void accessDenied_shouldBe403WithNullCode() {
        assertEnvelope(handler.handleAccessDenied(new AccessDeniedException(SENTINEL)), HttpStatus.FORBIDDEN, null, "Access denied");
    }

    @Test
    void unexpected_shouldBe500Dfn013WithoutLeak() throws Exception {
        ResponseEntity<GenericResponse> response = handler.handleUnexpected(new IllegalStateException(SENTINEL, new RuntimeException(SENTINEL)));

        assertEnvelope(response, HttpStatus.INTERNAL_SERVER_ERROR, "DFN013", "Internal Server Error");
        assertNoLeak(response);
    }

    @Test
    void frameworkErrorResponse_shouldKeepItsStatus() {
        ResponseEntity<GenericResponse> response = handler.handleUnexpected(new HttpMediaTypeNotSupportedException(SENTINEL));

        assertEnvelope(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DFN003", "Unsupported Media Type");
    }

    // ---------- correlation id ----------

    @Test
    void correlationId_shouldComeFromMdcAndFallBackForNonUuid() {
        MDCUtil.putCorrelationId(CORRELATION_ID);
        assertThat(handler.handleAccessDenied(new AccessDeniedException("x")).getBody().getCorrelationId())
                .isEqualTo(UUID.fromString(CORRELATION_ID));

        MDCUtil.putCorrelationId("not-a-uuid");
        assertThat(handler.handleAccessDenied(new AccessDeniedException("x")).getBody().getCorrelationId()).isNotNull();
    }
}
