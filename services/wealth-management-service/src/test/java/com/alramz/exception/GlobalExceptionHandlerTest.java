package com.alramz.exception;

import com.alramz.api.MmfApi;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import com.alramz.model.MmfBook;
import com.alramz.model.MmfSweepConfirmationItem;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private static final String CORRELATION_ID = "2afe4a42-44f7-4e12-aae7-df0e42d4d757";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    // ---------- typed exceptions ----------

    @Test
    void applicationException_shouldReturn400WithOwnCodeAndMessage() {
        ResponseEntity<GenericResponse> response = handler.handleApplication(
                new ApplicationException(ErrorCode.SWEEP_IN_DATES_INCOMPLETE, "Provide both fromDate and toDate, or neither"));

        assertError(response, HttpStatus.BAD_REQUEST, "MMF010");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Provide both fromDate and toDate, or neither");
        assertThat(response.getBody().getResponse()).isNull();
    }

    @Test
    void notFound_shouldReturn404() {
        ResponseEntity<GenericResponse> response = handler.handleNotFound(
                new MmfNotFoundException(ErrorCode.ACCOUNT_NOT_FOUND, "Account 2939418 not found in book CAPITAL"));

        assertError(response, HttpStatus.NOT_FOUND, "MMF004");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Account 2939418 not found in book CAPITAL");
    }

    @Test
    void conflict_shouldReturn409() {
        ResponseEntity<GenericResponse> response = handler.handleConflict(
                new MmfConflictException(ErrorCode.CONFIRM_CONFLICT, "Sweeps unknown or not pending: CAPITAL 2170412"));

        assertError(response, HttpStatus.CONFLICT, "MMF033");
    }

    @Test
    void backend_withCode_shouldReturn500WithThatCode() {
        ResponseEntity<GenericResponse> response = handler.handleBackend(
                new MmfBackendException(ErrorCode.POSITIONS_CLIENTS_NOT_CONFIGURED, "Custodian client ids not configured", null),
                handlerMethod(ErrorCode.OP_CONFIRM));

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "MMF065");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Custodian client ids not configured");
    }

    @Test
    void backend_withNullCode_shouldUseOperationInternalCodeAndFixedText() {
        ResponseEntity<GenericResponse> response = handler.handleBackend(
                new MmfBackendException(null, "Unable to load SQL mmf.account.find", new java.io.IOException("x")),
                handlerMethod(ErrorCode.OP_ACCOUNT));

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "MMF005");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Internal Server Error");
    }

    @Test
    void backendUnavailable_shouldReturn503() {
        ResponseEntity<GenericResponse> response = handler.handleUnavailable(
                new MmfBackendUnavailableException(ErrorCode.PROFIT_UNAVAILABLE, "MMF data source unavailable", null));

        assertError(response, HttpStatus.SERVICE_UNAVAILABLE, "MMF046");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("MMF data source unavailable");
    }

    // ---------- data access classification ----------

    @ParameterizedTest(name = "{0} unreachable -> {1}")
    @CsvSource({
            "getMmfAccount, MMF006",
            "listMmfSweepIns, MMF016",
            "listMmfSweepOuts, MMF026",
            "confirmMmfSweepIns, MMF036",
            "listMmfCustodianCash, MMF056",
            "listMmfCustodianPositions, MMF067"
    })
    void resourceFailures_shouldReturn503PerOperation(String op, String code) {
        List<Exception> failures = List.of(
                new DataAccessResourceFailureException("down"),
                new CannotGetJdbcConnectionException("no connection"),
                new TransientDataAccessResourceException("transient"),
                new CannotCreateTransactionException("tx", new CannotGetJdbcConnectionException("no connection")));

        for (Exception failure : failures) {
            ResponseEntity<GenericResponse> response = handler.handleDataAccess(failure, handlerMethod(op));

            assertError(response, HttpStatus.SERVICE_UNAVAILABLE, code);
            assertThat(response.getBody().getErrorMsg()).isEqualTo("MMF data source unavailable");
        }
    }

    @ParameterizedTest(name = "{0} sql error -> {1}")
    @CsvSource({
            "getMmfAccount, MMF005",
            "listMmfSweepIns, MMF015",
            "listMmfSweepOuts, MMF025",
            "confirmMmfSweepIns, MMF035",
            "listMmfCustodianCash, MMF055",
            "listMmfCustodianPositions, MMF066"
    })
    void otherDataAccessErrors_shouldReturn500PerOperationWithoutOraText(String op, String code) {
        DataAccessException ex = new BadSqlGrammarException("query", "SELECT 1",
                new SQLException("ORA-00942: table or view does not exist"));

        ResponseEntity<GenericResponse> response = handler.handleDataAccess(ex, handlerMethod(op));

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, code);
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Internal Server Error").doesNotContain("ORA");
    }

    @Test
    void cannotCreateTransaction_withoutResourceCause_shouldBe500() {
        ResponseEntity<GenericResponse> response = handler.handleDataAccess(
                new CannotCreateTransactionException("tx", new IllegalStateException("x")), handlerMethod(ErrorCode.OP_CONFIRM));

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "MMF035");
    }

    @Test
    void isResourceFailure_shouldWalkCauseChain() {
        assertThat(GlobalExceptionHandler.isResourceFailure(
                new RuntimeException(new IllegalStateException(new CannotGetJdbcConnectionException("x"))))).isTrue();
        assertThat(GlobalExceptionHandler.isResourceFailure(new RuntimeException("x"))).isFalse();
        assertThat(GlobalExceptionHandler.isResourceFailure(null)).isFalse();
    }

    // ---------- framework input failures ----------

    @Test
    void missingParameter_book_shouldReturnMMF002() {
        ResponseEntity<GenericResponse> response = handler.handleMissingParameter(
                new MissingServletRequestParameterException("book", "MmfBook"), handlerMethod(ErrorCode.OP_ACCOUNT));

        assertError(response, HttpStatus.BAD_REQUEST, "MMF002");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("book is required");
    }

    @Test
    void missingParameter_clientId_shouldReturnMMF050() {
        ResponseEntity<GenericResponse> response = handler.handleMissingParameter(
                new MissingServletRequestParameterException("clientId", "List"), handlerMethod(ErrorCode.OP_CASH));

        assertError(response, HttpStatus.BAD_REQUEST, "MMF050");
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "getMmfAccount, book, MMF003",
            "listMmfSweepIns, fromDate, MMF011",
            "listMmfSweepOuts, toDate, MMF021",
            "listMmfCustodianPositions, asOfDate, MMF060"
    })
    void typeMismatch_shouldResolvePerOperation(String op, String param, String code) {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "FOO", MmfBook.class, param, null, new IllegalArgumentException("bad"));

        assertError(handler.handleTypeMismatch(ex, handlerMethod(op)), HttpStatus.BAD_REQUEST, code);
    }

    @Test
    void methodArgumentNotValid_shouldMapFirstFieldErrorPerOperation() {
        assertError(handler.handleNotValid(notValid("source", "NotNull"), handlerMethod(ErrorCode.OP_CONFIRM)),
                HttpStatus.BAD_REQUEST, "MMF031");
        assertError(handler.handleNotValid(notValid("sweepNo", "Min"), handlerMethod(ErrorCode.OP_CONFIRM)),
                HttpStatus.BAD_REQUEST, "MMF032");
        assertError(handler.handleNotValid(notValid("sweepNo", "NotNull"), handlerMethod(ErrorCode.OP_CONFIRM)),
                HttpStatus.BAD_REQUEST, "MMF031");
        assertError(handler.handleNotValid(notValid("accountNumber", "Pattern"), handlerMethod(ErrorCode.OP_PROFIT)),
                HttpStatus.BAD_REQUEST, "MMF042");
        assertError(handler.handleNotValid(notValid("items", "Size"), handlerMethod(ErrorCode.OP_PROFIT)),
                HttpStatus.BAD_REQUEST, "MMF090");
    }

    @Test
    void methodArgumentNotValid_withoutFieldErrors_shouldFallBackToBodyCode() {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "body");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyParameter(), result);

        assertError(handler.handleNotValid(ex, handlerMethod(ErrorCode.OP_CONFIRM)), HttpStatus.BAD_REQUEST, "MMF030");
    }

    @Test
    void methodValidation_valueParameter_shouldUseParameterNameAndLastCode() {
        Method method = mmfMethod(ErrorCode.OP_CASH);
        MethodParameter param = new MethodParameter(method, 0);

        HandlerMethodValidationException size = methodValidation(method,
                new ParameterValidationResult(param, List.of(), List.of(resolvable("Size.clientId", "Size")),
                        null, null, null, (r, c) -> null));
        HandlerMethodValidationException pattern = methodValidation(method,
                new ParameterValidationResult(param, "12ab", List.of(resolvable("Pattern.clientId", "Pattern")),
                        List.of("12ab"), 0, null, (r, c) -> null));

        assertError(handler.handleMethodValidation(size), HttpStatus.BAD_REQUEST, "MMF052");
        assertError(handler.handleMethodValidation(pattern), HttpStatus.BAD_REQUEST, "MMF051");
    }

    @Test
    void methodValidation_requestBodySize_shouldUseBodyCode() {
        Method method = mmfMethod(ErrorCode.OP_PROFIT);
        HandlerMethodValidationException ex = methodValidation(method,
                new ParameterValidationResult(new MethodParameter(method, 0), List.of(),
                        List.of(resolvable("Size.body", "Size")), null, null, null, (r, c) -> null));

        ResponseEntity<GenericResponse> response = handler.handleMethodValidation(ex);

        assertError(response, HttpStatus.BAD_REQUEST, "MMF040");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Request must contain 1-5000 items");
    }

    @Test
    void methodValidation_beanElement_shouldPrefixItemNumber() {
        Method method = mmfMethod(ErrorCode.OP_CONFIRM);
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Object(), "item");
        errors.addError(new FieldError("item", "sweepNo", 0L, false, new String[] {"Min.item.sweepNo", "Min"}, null, "must be >= 1"));
        ParameterErrors parameterErrors = new ParameterErrors(new MethodParameter(method, 0), null, errors, List.of(), 1, null);

        ResponseEntity<GenericResponse> response = handler.handleMethodValidation(methodValidation(method, parameterErrors));

        assertError(response, HttpStatus.BAD_REQUEST, "MMF032");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Item 2: sweepNo must be a positive integer");
    }

    @Test
    void methodValidation_crossParameterOnly_shouldReturnMMF090() {
        Method method = mmfMethod(ErrorCode.OP_CASH);
        HandlerMethodValidationException ex = new HandlerMethodValidationException(
                MethodValidationResult.create(new Object(), method, List.of(), List.of(resolvable("Cross"))));

        assertError(handler.handleMethodValidation(ex), HttpStatus.BAD_REQUEST, "MMF090");
    }

    @Test
    void constraintViolation_listElementPattern_shouldMapToClientIdCodeWithoutItemPrefix() {
        ResponseEntity<GenericResponse> response = handler.handleConstraintViolation(
                violations(ErrorCode.OP_CASH, List.of("2001214", "12ab")), handlerMethod(ErrorCode.OP_CASH));

        assertError(response, HttpStatus.BAD_REQUEST, "MMF051");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("clientId must be 1-9 digits");
    }

    @Test
    void constraintViolation_sizeOnBody_winsOverElementErrors() {
        List<MmfSweepConfirmationItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            items.add(new MmfSweepConfirmationItem().sweepNo(i == 5 ? 0L : i + 1L).source(MmfBook.CAPITAL));
        }

        assertError(handler.handleConstraintViolation(violations(ErrorCode.OP_CONFIRM, items), handlerMethod(ErrorCode.OP_CONFIRM)),
                HttpStatus.BAD_REQUEST, "MMF030");
    }

    @Test
    void constraintViolation_beanElement_shouldUseLowestItemAndPrefix() {
        List<MmfSweepConfirmationItem> items = List.of(
                new MmfSweepConfirmationItem().sweepNo(1L).source(MmfBook.CAPITAL),
                new MmfSweepConfirmationItem().sweepNo(0L).source(MmfBook.CAPITAL),
                new MmfSweepConfirmationItem().sweepNo(3L));

        ResponseEntity<GenericResponse> response = handler.handleConstraintViolation(
                violations(ErrorCode.OP_CONFIRM, items), handlerMethod(ErrorCode.OP_CONFIRM));

        assertError(response, HttpStatus.BAD_REQUEST, "MMF032");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Item 2: sweepNo must be a positive integer");
    }

    @Test
    void constraintViolation_pathPattern_shouldMapToAccountCode() {
        assertError(handler.handleConstraintViolation(violations(ErrorCode.OP_ACCOUNT, "12ab", MmfBook.CAPITAL),
                handlerMethod(ErrorCode.OP_ACCOUNT)), HttpStatus.BAD_REQUEST, "MMF001");
    }

    @Test
    void constraintViolation_emptySetOrNoHandler_shouldReturnMMF090() {
        assertError(handler.handleConstraintViolation(new ConstraintViolationException(Set.of()), handlerMethod(ErrorCode.OP_CASH)),
                HttpStatus.BAD_REQUEST, "MMF090");
        assertError(handler.handleConstraintViolation(violations(ErrorCode.OP_CASH, List.of("12ab")), null),
                HttpStatus.BAD_REQUEST, "MMF090");
    }

    @Test
    void notReadable_withJacksonPath_shouldMapFieldAndItemIndex() {
        InvalidFormatException jackson = InvalidFormatException.from(null, "Cannot deserialize", "X", MmfBook.class);
        jackson.prependPath(new JacksonException.Reference(null, "source"));
        jackson.prependPath(new JacksonException.Reference(null, 2));
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad", jackson, new MockHttpInputMessage(new byte[0]));

        ResponseEntity<GenericResponse> response = handler.handleNotReadable(ex, handlerMethod(ErrorCode.OP_PROFIT));

        assertError(response, HttpStatus.BAD_REQUEST, "MMF043");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Item 3: source must be CAPITAL or MMF");
    }

    @Test
    void notReadable_withoutPath_shouldUseBodyCodeOrMMF090() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Required request body is missing",
                new MockHttpInputMessage(new byte[0]));

        assertError(handler.handleNotReadable(ex, handlerMethod(ErrorCode.OP_CONFIRM)), HttpStatus.BAD_REQUEST, "MMF030");
        assertError(handler.handleNotReadable(ex, handlerMethod(ErrorCode.OP_PROFIT)), HttpStatus.BAD_REQUEST, "MMF040");
        assertError(handler.handleNotReadable(ex, null), HttpStatus.BAD_REQUEST, "MMF090");
    }

    // ---------- security and catch-all ----------

    @Test
    void accessDenied_shouldReturn403WithNullCodeAndNoRoleText() {
        ResponseEntity<GenericResponse> response = handler.handleAccessDenied(
                new AccessDeniedException("Insufficient role. Required one of: [APP_WEALTH_MANAGEMENT_SERVICE_USER]"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getResponseCode()).isEqualTo("403");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Forbidden");
        assertThat(response.getBody().getErrorCode()).isNull();
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Access denied").doesNotContain("APP_WEALTH");
    }

    @Test
    void generic_shouldReturnOperationInternalCodeAndNeverLeakMessage() {
        ResponseEntity<GenericResponse> response = handler.handleGeneric(
                new IllegalStateException("leaked internal detail"), handlerMethod(ErrorCode.OP_POSITIONS));

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "MMF066");
        assertThat(response.getBody().getErrorMsg()).isEqualTo("Internal Server Error");
    }

    @Test
    void generic_withoutHandlerMethod_shouldReturnMMF090() {
        assertError(handler.handleGeneric(new IllegalStateException("x"), null), HttpStatus.INTERNAL_SERVER_ERROR, "MMF090");
    }

    @Test
    void correlationId_shouldEchoMdc() {
        MDCUtil.putCorrelationId(CORRELATION_ID);

        ResponseEntity<GenericResponse> response = handler.handleApplication(
                new ApplicationException(ErrorCode.SWEEP_IN_RANGE_INVALID, "x"));

        assertThat(response.getBody().getCorrelationId()).hasToString(CORRELATION_ID);
    }

    // ---------- helpers ----------

    private static void assertError(ResponseEntity<GenericResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo(String.valueOf(status.value()));
        assertThat(response.getBody().getResponseMessage()).isEqualTo(status.getReasonPhrase());
        assertThat(response.getBody().getErrorCode()).isEqualTo(code);
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }

    private static Method mmfMethod(String name) {
        return Arrays.stream(MmfApi.class.getMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static HandlerMethod handlerMethod(String operationId) {
        return new HandlerMethod(new Object(), mmfMethod(operationId));
    }

    private static ConstraintViolationException violations(String operationId, Object... args) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Set<ConstraintViolation<MmfApi>> found = factory.getValidator().forExecutables()
                    .validateParameters(mock(MmfApi.class), mmfMethod(operationId), args);
            assertThat(found).isNotEmpty();
            return new ConstraintViolationException(found);
        }
    }

    private static DefaultMessageSourceResolvable resolvable(String... codes) {
        return new DefaultMessageSourceResolvable(codes, "invalid");
    }

    private static HandlerMethodValidationException methodValidation(Method method, ParameterValidationResult result) {
        return new HandlerMethodValidationException(MethodValidationResult.create(new Object(), method, List.of(result)));
    }

    private static MethodArgumentNotValidException notValid(String field, String code) {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "item");
        result.addError(new FieldError("item", field, null, false, new String[] {code + ".item." + field, code}, null, "invalid"));
        return new MethodArgumentNotValidException(dummyParameter(), result);
    }

    private static MethodParameter dummyParameter() {
        return new MethodParameter(mmfMethod(ErrorCode.OP_CONFIRM), 0);
    }
}
