package com.alramz.exception;

import com.alramz.model.GenericResponse;
import com.alramz.logging.util.MDCUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    @Test
    void handleApplication_shouldReturn400WithExceptionsOwnErrorCode() {
        ApplicationException ex = new ApplicationException(ErrorCode.POST_DESC_INVALID, "too long");

        ResponseEntity<GenericResponse> response = handler.handleApplication(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("FNX004");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("too long");
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }

    @Test
    void handleValidation_shouldMapPostedUserIdToFNX001() {
        assertFieldMapsToCode("postedUserId", "FNX001");
    }

    @Test
    void handleValidation_shouldMapUsernameToFNX002() {
        assertFieldMapsToCode("username", "FNX002");
    }

    @Test
    void handleValidation_shouldMapPostedByToFNX003() {
        assertFieldMapsToCode("postedBy", "FNX003");
    }

    @Test
    void handleValidation_shouldMapPostDescToFNX004() {
        assertFieldMapsToCode("postDesc", "FNX004");
    }

    @Test
    void handleValidation_shouldMapTagSymbolToFNX005() {
        assertFieldMapsToCode("tagSymbol", "FNX005");
    }

    @Test
    void handleValidation_shouldFallBackToFNX090ForUnmappedField() {
        assertFieldMapsToCode("someUnknownField", "FNX090");
    }

    private void assertFieldMapsToCode(String field, String expectedCode) {
        MethodArgumentNotValidException ex = buildValidationException(field, "must not be blank");

        ResponseEntity<GenericResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo(expectedCode);
    }

    @Test
    void handleNotReadable_shouldReturn400WithFNX090() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        ResponseEntity<GenericResponse> response = handler.handleNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("FNX090");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Malformed JSON request");
    }

    @Test
    void handleFinouxIntegration_shouldReturn500WithExceptionsOwnErrorCode() {
        FinouxIntegrationException ex = new FinouxIntegrationException(ErrorCode.FINOUX_REJECTED, "isSuccess=false");

        ResponseEntity<GenericResponse> response = handler.handleFinouxIntegration(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo("FNX011");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("isSuccess=false");
    }

    @Test
    void handleFinouxUnavailable_shouldReturn503WithFNX012() {
        FinouxUnavailableException ex = new FinouxUnavailableException("Finoux community service unavailable", new RuntimeException("timeout"));

        ResponseEntity<GenericResponse> response = handler.handleFinouxUnavailable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getErrorCode()).isEqualTo("FNX012");
    }

    @Test
    void handleAccessDenied_shouldReturn403NotBeSwallowedByGenericHandler() {
        // JwtSecuredAspect throws this when a valid JWT lacks ROLE_APP_UTILITY; it must stay a 403.
        AccessDeniedException ex = new AccessDeniedException("Insufficient role. Required one of: [APP_UTILITY]");

        ResponseEntity<GenericResponse> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Access denied");
        assertThat(response.getBody().getErrorMsg()).doesNotContain("APP_UTILITY");
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }

    @Test
    void handleGeneric_shouldReturn500WithFNX013AndNeverLeakExceptionMessage() {
        RuntimeException ex = new RuntimeException("leaked internal stack trace detail");

        ResponseEntity<GenericResponse> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo("FNX013");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getErrorMsg()).doesNotContain("leaked internal stack trace detail");
    }

    @Test
    void handleApplication_shouldEchoTheRequestCorrelationId() {
        MDCUtil.putCorrelationId("2afe4a42-44f7-4e12-aae7-df0e42d4d757");

        ResponseEntity<GenericResponse> response = handler.handleApplication(
                new ApplicationException(ErrorCode.POST_DESC_INVALID, "too long"));

        assertThat(response.getBody().getCorrelationId().toString()).isEqualTo("2afe4a42-44f7-4e12-aae7-df0e42d4d757");
    }

    private MethodArgumentNotValidException buildValidationException(String field, String message) {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "communityPostRequest");
        bindingResult.addError(new FieldError("communityPostRequest", field, message));
        Method method;
        try {
            method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        MethodParameter parameter = new MethodParameter(method, 0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    @SuppressWarnings("unused")
    private void dummyTarget(String arg) {
        // used only to build a MethodParameter for MethodArgumentNotValidException in tests
    }

    @Test
    void handleValidation_shouldJoinMultipleFieldErrorMessages() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "communityPostRequest");
        bindingResult.addError(new FieldError("communityPostRequest", "username", "must not be blank"));
        bindingResult.addError(new FieldError("communityPostRequest", "postedBy", "size must be between 1 and 45"));
        Method method;
        try {
            method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ResponseEntity<GenericResponse> response = handler.handleValidation(ex);

        assertThat(response.getBody().getErrorMsg()).contains("username").contains("postedBy");
        assertThat(response.getBody().getErrorCode()).isEqualTo("FNX002");
    }
}
