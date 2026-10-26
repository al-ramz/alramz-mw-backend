package com.alramz.controllers;

import com.alramz.exception.GlobalExceptionHandler;
import com.alramz.logging.filter.CorrelationIdFilter;
import com.alramz.model.MmfAccount;
import com.alramz.model.MmfBook;
import com.alramz.model.MmfCustodianCash;
import com.alramz.model.MmfCustodianPosition;
import com.alramz.model.MmfMarket;
import com.alramz.model.MmfProfitDistributionResult;
import com.alramz.model.MmfSweep;
import com.alramz.service.MmfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Spring MVC dispatch (binding, method validation, Jackson, the advice) for MmfController with a
 * mocked service. Not @WebMvcTest: a standalone MockMvc that reuses the application context's own
 * message converters, validator, conversion service, correlation-id filter and method-validation
 * proxy, so it exercises the Jackson major, spring.jackson settings and validation path MVC actually
 * runs with. (The MMF beans themselves are off in tests: brok is disabled.)
 */
@SpringBootTest
class MmfApiDispatchTest {

    private static final String BASE = "/api/v1/mmf";
    private static final String CORRELATION_ID = "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33";

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @Autowired
    @Qualifier("mvcValidator")
    private Validator mvcValidator;

    @Autowired
    @Qualifier("mvcConversionService")
    private FormattingConversionService mvcConversionService;

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    // MmfApi is @Validated, so at runtime constraints are enforced by this post-processor's proxy, not by MVC.
    @Autowired
    private MethodValidationPostProcessor methodValidationPostProcessor;

    private MmfService mmfService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mmfService = mock(MmfService.class);
        Object controller = methodValidationPostProcessor.postProcessAfterInitialization(
                new MmfController(mmfService), "mmfController");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(handlerAdapter.getMessageConverters().toArray(new HttpMessageConverter<?>[0]))
                .setValidator(mvcValidator)
                .setConversionService(mvcConversionService)
                .addFilters(correlationIdFilter)
                .build();
    }

    // ---------- account ----------

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "/accounts/12ab?book=CAPITAL, MMF001",
            "/accounts/1234567890?book=CAPITAL, MMF001",
            "/accounts/2939418, MMF002",
            "/accounts/2939418?book=FOO, MMF003",
            "/sweep-ins?fromDate=2025-02-31&toDate=2025-03-01, MMF011",
            "/sweep-ins?fromDate=2025-01-01&toDate=30-12-2019, MMF011",
            "/sweep-outs?fromDate=2025-02-31&toDate=2025-03-01, MMF021",
            "/custodian-cash, MMF050",
            "/custodian-cash?clientId=12ab, MMF051",
            "/custodian-cash?clientId=2001214&clientId=1234567890, MMF051",
            "/custodian-positions?asOfDate=2026-13-01, MMF060"
    })
    void getInputErrors_shouldReturn400WithOperationCodeAndNeverCallService(String path, String code) throws Exception {
        expectError(mockMvc.perform(get(BASE + path)), 400, code);
        verifyNoInteractions(mmfService);
    }

    @Test
    void account_found_shouldReturnCamelCaseEnvelopeAndEchoCorrelationId() throws Exception {
        when(mmfService.getAccount("2939418", MmfBook.CAPITAL)).thenReturn(new MmfAccount()
                .clientId("1100206936164").clientNameEn("EN").clientNameAr("AR")
                .accountNumber("2939418").accountName("NAME").baseCurrency("AED"));

        mockMvc.perform(get(BASE + "/accounts/2939418?book=CAPITAL").header("X-Correlation-ID", CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("200"))
                .andExpect(jsonPath("$.responseMessage").value("OK"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.response.account.clientNameEn").value("EN"))
                .andExpect(jsonPath("$.response.account.baseCurrency").value("AED"));
    }

    @Test
    void errorBody_shouldEchoCorrelationIdHeader() throws Exception {
        mockMvc.perform(get(BASE + "/accounts/2939418?book=FOO").header("X-Correlation-ID", CORRELATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.errorMsg").value("book must be CAPITAL or MMF"));
    }

    // ---------- sweeps ----------

    @Test
    void sweepIns_shouldSerializeIsoDateAndNumericAmount() throws Exception {
        when(mmfService.listSweeps("W", LocalDate.of(2019, 12, 1), LocalDate.of(2019, 12, 31))).thenReturn(List.of(
                new MmfSweep().sweepNo(52L).date(LocalDate.of(2019, 12, 30)).accountNumber("2944237")
                        .amount(new BigDecimal("3838.02")).source(MmfBook.CAPITAL)));

        mockMvc.perform(get(BASE + "/sweep-ins?fromDate=2019-12-01&toDate=2019-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.sweepIns[0].date").value("2019-12-30"))
                .andExpect(jsonPath("$.response.sweepIns[0].sweepNo").value(52))
                .andExpect(jsonPath("$.response.sweepIns[0].amount").value(3838.02))
                .andExpect(jsonPath("$.response.sweepIns[0].accountNumber").value("2944237"))
                .andExpect(jsonPath("$.response.sweepIns[0].source").value("CAPITAL"));
    }

    @Test
    void sweepOuts_withoutDates_shouldPassNulls() throws Exception {
        when(mmfService.listSweeps("O", null, null)).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/sweep-outs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.sweepOuts").isEmpty());
    }

    // ---------- confirm ----------

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "[]| MMF030| Request must contain 1-1000 items",
            "[{\"sweepNo\": 1}]| MMF031| Item 1: source must be CAPITAL or MMF",
            "[{\"source\": \"CAPITAL\"}]| MMF031| Item 1: sweepNo is required",
            "[{\"sweepNo\": 1, \"source\": \"CAPITAL\"}, {\"sweepNo\": 2, \"source\": \"X\"}]| MMF031| Item 2: source must be CAPITAL or MMF",
            "[{\"sweepNo\": 0, \"source\": \"CAPITAL\"}]| MMF032| Item 1: sweepNo must be a positive integer",
            "[{\"sweepNo\": -5, \"source\": \"MMF\"}]| MMF032| Item 1: sweepNo must be a positive integer",
            "[{\"sweepNo\": 1.5, \"source\": \"CAPITAL\"}]| MMF032| Item 1: sweepNo must be a positive integer",
            "[{\"sweepNo\": \"abc\", \"source\": \"CAPITAL\"}]| MMF032| Item 1: sweepNo must be a positive integer",
            "[{\"sweepNo\": 1, \"source\": \"CAPITAL\"}| MMF030| Request must contain 1-1000 items",
            "{\"sweepNo\": 1, \"source\": \"CAPITAL\"}| MMF030| Request must contain 1-1000 items"
    })
    void confirm_inputErrors(String body, String code, String message) throws Exception {
        expectError(mockMvc.perform(json(post(BASE + "/sweep-ins/confirmations"), body)), 400, code)
                .andExpect(jsonPath("$.errorMsg").value(message));
        verifyNoInteractions(mmfService);
    }

    @Test
    void confirm_missingBody_shouldReturnMMF030() throws Exception {
        expectError(mockMvc.perform(post(BASE + "/sweep-ins/confirmations").contentType(MediaType.APPLICATION_JSON)), 400, "MMF030");
        verifyNoInteractions(mmfService);
    }

    @Test
    void confirm_1001Items_shouldReturnMMF030() throws Exception {
        expectError(mockMvc.perform(json(post(BASE + "/sweep-ins/confirmations"), confirmItems(1001))), 400, "MMF030");
        verifyNoInteractions(mmfService);
    }

    @Test
    void confirm_1000Items_shouldReachService() throws Exception {
        mockMvc.perform(json(post(BASE + "/sweep-ins/confirmations"), confirmItems(1000)))
                .andExpect(status().isOk());
        verify(mmfService).confirmSweepIns(anyList());
    }

    // ---------- profit ----------

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "[]| MMF040| Request must contain 1-5000 items",
            "[{\"sweepDate\": \"2025-02-31\", \"accountNumber\": \"1005\", \"amount\": 1, \"referenceId\": \"1\", \"source\": \"CAPITAL\"}]| MMF041| Item 1: sweepDate must be an ISO-8601 date (yyyy-MM-dd)",
            "[{\"accountNumber\": \"1005\", \"amount\": 1, \"referenceId\": \"1\", \"source\": \"CAPITAL\"}]| MMF041| Item 1: sweepDate must be an ISO-8601 date (yyyy-MM-dd)",
            "[{\"sweepDate\": \"2025-04-20\", \"accountNumber\": \"1005\", \"amount\": 0, \"referenceId\": \"1\", \"source\": \"CAPITAL\"}]| MMF041| Item 1: amount must be > 0 with at most 3 decimals",
            "[{\"sweepDate\": \"2025-04-20\", \"accountNumber\": \"1005\", \"amount\": -2, \"referenceId\": \"1\", \"source\": \"CAPITAL\"}]| MMF041| Item 1: amount must be > 0 with at most 3 decimals",
            "[{\"sweepDate\": \"2025-04-20\", \"accountNumber\": \"1005\", \"amount\": \"x\", \"referenceId\": \"1\", \"source\": \"CAPITAL\"}]| MMF041| Item 1: amount must be > 0 with at most 3 decimals",
            "[{\"sweepDate\": \"2025-04-20\", \"accountNumber\": \"1005\", \"amount\": 1, \"referenceId\": \"12a\", \"source\": \"CAPITAL\"}]| MMF041| Item 1: referenceId must be 1-15 digits",
            "[{\"sweepDate\": \"2025-04-20\", \"accountNumber\": \"1234567890\", \"amount\": 1, \"referenceId\": \"1\", \"source\": \"CAPITAL\"}]| MMF042| Item 1: accountNumber must be 1-9 digits",
            "[{\"sweepDate\": \"2025-04-20\", \"amount\": 1, \"referenceId\": \"1\", \"source\": \"CAPITAL\"}]| MMF042| Item 1: accountNumber must be 1-9 digits",
            "[{\"sweepDate\": \"2025-04-20\", \"accountNumber\": \"1005\", \"amount\": 1, \"referenceId\": \"1\", \"source\": \"X\"}]| MMF043| Item 1: source must be CAPITAL or MMF",
            "[{\"sweepDate\": \"2025-04-20\", \"accountNumber\": \"1005\", \"amount\": 1, \"referenceId\": \"1\"}]| MMF043| Item 1: source must be CAPITAL or MMF",
            "not json| MMF040| Request must contain 1-5000 items"
    })
    void profit_inputErrors(String body, String code, String message) throws Exception {
        expectError(mockMvc.perform(json(post(BASE + "/profit-distributions"), body)), 400, code)
                .andExpect(jsonPath("$.errorMsg").value(message));
        verifyNoInteractions(mmfService);
    }

    @Test
    void profit_5001Items_shouldReturnMMF040() throws Exception {
        String body = IntStream.rangeClosed(1, 5001)
                .mapToObj(i -> "{\"sweepDate\":\"2025-04-20\",\"accountNumber\":\"1005\",\"amount\":1,\"referenceId\":\"" + i + "\",\"source\":\"CAPITAL\"}")
                .collect(Collectors.joining(",", "[", "]"));

        expectError(mockMvc.perform(json(post(BASE + "/profit-distributions"), body)), 400, "MMF040");
        verifyNoInteractions(mmfService);
    }

    @Test
    void profit_allCreated_shouldReturn201() throws Exception {
        when(mmfService.createProfitDistributions(anyList())).thenReturn(List.of(result("CREATED", 2170513L, null)));

        mockMvc.perform(json(post(BASE + "/profit-distributions"), profitBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responseCode").value("201"))
                .andExpect(jsonPath("$.responseMessage").value("Created"))
                .andExpect(jsonPath("$.response.results[0].status").value("CREATED"))
                .andExpect(jsonPath("$.response.results[0].sweepNo").value(2170513));
    }

    @Test
    void profit_mixed_shouldReturn207() throws Exception {
        when(mmfService.createProfitDistributions(anyList())).thenReturn(List.of(
                result("FAILED", null, "MMF047"), result("CREATED", 2170514L, null)));

        mockMvc.perform(json(post(BASE + "/profit-distributions"), profitBody()))
                .andExpect(status().is(207))
                .andExpect(jsonPath("$.responseCode").value("207"))
                .andExpect(jsonPath("$.responseMessage").value("Multi-Status"))
                .andExpect(jsonPath("$.response.results[0].errorCode").value("MMF047"));
    }

    // ---------- custodian ----------

    @Test
    void custodianCash_501Ids_shouldReturnMMF052() throws Exception {
        expectError(mockMvc.perform(get(BASE + "/custodian-cash" + clientIdQuery(501))), 400, "MMF052");
        verifyNoInteractions(mmfService);
    }

    @Test
    void custodianCash_500Ids_shouldBeAcceptedAndCashBalanceIsANumber() throws Exception {
        when(mmfService.listCustodianCash(anyList())).thenReturn(List.of(new MmfCustodianCash()
                .clientMainId("1100106001214").clientId("1").clientName("N").cashBalance(new BigDecimal("1250.50"))));

        mockMvc.perform(get(BASE + "/custodian-cash" + clientIdQuery(500)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.custodianCash[0].clientMainId").value("1100106001214"))
                .andExpect(jsonPath("$.response.custodianCash[0].cashBalance").isNumber());
    }

    @Test
    void custodianPositions_shouldSerializePriceDateAndMarket() throws Exception {
        when(mmfService.listCustodianPositions(eq(LocalDate.of(2025, 4, 20)))).thenReturn(List.of(new MmfCustodianPosition()
                .clientId("2001214").isin("AEE000301011").symbol("EMAAR").quantity(new BigDecimal("15000"))
                .closePrice(new BigDecimal("9.85")).currency("AED").market(MmfMarket.DFM).priceDate(LocalDate.of(2025, 4, 20))));

        mockMvc.perform(get(BASE + "/custodian-positions?asOfDate=2025-04-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.custodianPositions[0].priceDate").value("2025-04-20"))
                .andExpect(jsonPath("$.response.custodianPositions[0].market").value("DFM"))
                .andExpect(jsonPath("$.response.custodianPositions[0].closePrice").value(9.85));
    }

    @Test
    void custodianPositions_withoutDate_shouldPassNull() throws Exception {
        when(mmfService.listCustodianPositions(any())).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/custodian-positions")).andExpect(status().isOk());
        verify(mmfService).listCustodianPositions(null);
    }

    // ---------- helpers ----------

    private static ResultActions expectError(ResultActions actions, int status, String code) throws Exception {
        return actions.andExpect(status().is(status))
                .andExpect(jsonPath("$.responseCode").value(String.valueOf(status)))
                .andExpect(jsonPath("$.errorCode").value(code))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String confirmItems(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> "{\"sweepNo\":" + i + ",\"source\":\"CAPITAL\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String clientIdQuery(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> "clientId=" + i)
                .collect(Collectors.joining("&", "?", ""));
    }

    private static String profitBody() {
        return "[{\"sweepDate\":\"2025-04-20\",\"accountNumber\":\"1005\",\"amount\":2500.75,\"referenceId\":\"987654321\",\"source\":\"CAPITAL\"}]";
    }

    private static MmfProfitDistributionResult result(String status, Long sweepNo, String errorCode) {
        return new MmfProfitDistributionResult().referenceId("987654321").accountNumber("1005").book(MmfBook.CAPITAL)
                .status(MmfProfitDistributionResult.StatusEnum.fromValue(status)).sweepNo(sweepNo).errorCode(errorCode);
    }
}
