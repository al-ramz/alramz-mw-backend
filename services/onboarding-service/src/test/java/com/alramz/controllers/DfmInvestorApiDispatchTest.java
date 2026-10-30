package com.alramz.controllers;

import com.alramz.config.DfmMemberApiProperties;
import com.alramz.config.ExternalDfmMemberApiConfig;
import com.alramz.config.JacksonConfig;
import com.alramz.dfm.DfmMemberApiClient;
import com.alramz.exception.ApplicationException;
import com.alramz.exception.DfmInvestorExceptionHandler;
import com.alramz.exception.GlobalExceptionHandler;
import com.alramz.jwt.aspect.JwtSecuredAspect;
import com.alramz.logging.advice.CorrelationIdResponseBodyAdvice;
import com.alramz.logging.filter.CorrelationIdFilter;
import com.alramz.model.DfmInvestorRegistration;
import com.alramz.service.DfmInvestorService;
import com.alramz.service.impl.DfmInvestorServiceImpl;
import com.alramz.service.impl.DfmInvestorServiceImplTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Spring MVC dispatch for DfmInvestorController: Jackson binding, Bean Validation, the JwtSecuredAspect and
 * advice ordering. Not @WebMvcTest (testing-patterns.md): a standalone MockMvc reusing the application context's own
 * converters, validator, conversion service and correlation-id filter (MmfApiDispatchTest precedent). Both advices
 * are registered, GlobalExceptionHandler first, to prove DfmInvestorExceptionHandler's @Order(-1) wins.
 */
@SpringBootTest
class DfmInvestorApiDispatchTest {

    private static final String PATH = "/api/v1/dfm/investors";
    private static final String CORRELATION_ID = "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33";
    private static final DfmMemberApiProperties PROPS =
            new DfmMemberApiProperties("https://dfm.test", "/memberapis/onboarding", "TEST01", "header-key", "body-key", 5, 5, List.of());
    private static final String OK_BODY = """
            {"response_code": "200", "response_msg": "OK", "member_reference": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
             "dfm_reference": "1b2c3d4e-5f60-4718-89ab-cdef01234567", "tracking_no": "2313258142",
             "cash_acc": "9400000", "cust_nin": "EGA0000000"}
            """;

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

    @Autowired
    private JwtSecuredAspect jwtSecuredAspect;

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private DfmInvestorService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DfmInvestorService.class);
        mockMvc = mvc(service);
        authenticate("ROLE_APP_DATA_VALIDATION");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockMvc mvc(DfmInvestorService dfmInvestorService) {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new DfmInvestorController(dfmInvestorService));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(jwtSecuredAspect);
        return MockMvcBuilders.standaloneSetup((Object) proxyFactory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler(), new DfmInvestorExceptionHandler(), new CorrelationIdResponseBodyAdvice())
                .setMessageConverters(handlerAdapter.getMessageConverters().toArray(new HttpMessageConverter<?>[0]))
                .setValidator(mvcValidator)
                .setConversionService(mvcConversionService)
                .addFilters(correlationIdFilter)
                .build();
    }

    /** The real service + client, with DFM replaced by a stubbed exchange. */
    private MockMvc mvcAgainstDfm(DfmMemberApiProperties props, AtomicInteger exchanges, Supplier<Mono<ClientResponse>> dfm) {
        ExternalDfmMemberApiConfig config = new ExternalDfmMemberApiConfig(props);
        DfmMemberApiClient client = new DfmMemberApiClient(
                WebClient.builder().exchangeFunction(request -> {
                    exchanges.incrementAndGet();
                    return dfm.get();
                }).build(),
                config.dfmMemberApiCircuitBreaker(CircuitBreakerRegistry.ofDefaults()),
                config.dfmMemberApiRetry(RetryRegistry.ofDefaults()),
                config.dfmMemberApiTimeLimiter(TimeLimiterRegistry.ofDefaults()),
                props,
                objectMapper);
        return mvc(new DfmInvestorServiceImpl(client));
    }

    private static Supplier<Mono<ClientResponse>> dfm(int status, String body) {
        return () -> Mono.just(ClientResponse.create(HttpStatus.valueOf(status))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private static void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("consumer", null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private String body(Consumer<ObjectNode> change) throws Exception {
        ObjectNode node = objectMapper.valueToTree(DfmInvestorServiceImplTest.validRequest());
        change.accept(node);
        return objectMapper.writeValueAsString(node);
    }

    private ResultActions postJson(MockMvc mvc, String json) throws Exception {
        return mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static ResultActions expectError(ResultActions actions, int status, String code) throws Exception {
        return actions.andExpect(status().is(status))
                .andExpect(jsonPath("$.responseCode").value(String.valueOf(status)))
                .andExpect(code == null ? jsonPath("$.errorCode").value(nullValue()) : jsonPath("$.errorCode").value(code))
                .andExpect(jsonPath("$.response").value(nullValue()))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    // ---------- success ----------

    @Test
    void success_shouldReturn201WithEnvelopeAndEchoedCorrelationId() throws Exception {
        when(service.register(any())).thenReturn(new DfmInvestorRegistration().nin("EGA0000000").cashTradingNumber("9400000"));

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Correlation-Id", CORRELATION_ID).content(body(n -> { })))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responseCode").value("201"))
                .andExpect(jsonPath("$.responseMessage").value("Created"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.errorCode").value(nullValue()))
                .andExpect(jsonPath("$.errorMsg").value(nullValue()))
                .andExpect(jsonPath("$.response.nin").value("EGA0000000"))
                .andExpect(jsonPath("$.response.cashTradingNumber").value("9400000"));
    }

    // ---------- security ----------

    @Test
    void tokenWithoutTheRole_shouldGet403WithNullErrorCode() throws Exception {
        authenticate("ROLE_APP_OTHER");

        expectError(postJson(mockMvc, body(n -> { })), 403, null)
                .andExpect(jsonPath("$.errorMsg").value("Access denied"));
        verifyNoInteractions(service);
    }

    // ---------- input validation ----------

    @Test
    void malformedJson_shouldBeDfn003() throws Exception {
        expectError(postJson(mockMvc, "{\"requestId\": "), 400, "DFN003")
                .andExpect(jsonPath("$.errorMsg").value("Malformed or invalid JSON request"));
        verifyNoInteractions(service);
    }

    @Test
    void badEnum_shouldBeDfn003() throws Exception {
        expectError(postJson(mockMvc, body(n -> n.put("gender", "X"))), 400, "DFN003");
    }

    @Test
    void badUuid_shouldBeDfn003() throws Exception {
        expectError(postJson(mockMvc, body(n -> n.put("requestId", "not-a-uuid"))), 400, "DFN003");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "noCountry, DFN001",
            "lowerCaseCountry, DFN001",
            "longCity, DFN002",
            "noCity, DFN002",
            "badMobile, DFN004",
            "longLine, DFN005",
            "badPhone, DFN003",
            "noFirstName, DFN003",
            "emptyPortfolio, DFN003",
            "badCrsCountry, DFN003"
    })
    void beanValidation_shouldMapFieldToCode(String input, String code) throws Exception {
        String json = body(n -> {
            ObjectNode address = (ObjectNode) n.get("address");
            switch (input) {
                case "noCountry" -> address.remove("country");
                case "lowerCaseCountry" -> address.put("country", "ae");
                case "longCity" -> address.put("city", "C".repeat(26));
                case "noCity" -> address.remove("city");
                case "badMobile" -> n.put("mobile", "0501234567");
                case "longLine" -> address.put("line", "L".repeat(41));
                case "badPhone" -> address.put("phone", "12");
                case "noFirstName" -> n.remove("firstName");
                case "emptyPortfolio" -> n.putArray("portfolioOptions");
                case "badCrsCountry" -> n.putObject("crs").putArray("countries").addObject().put("country", "x").put("hasTin", false);
                default -> throw new IllegalArgumentException(input);
            }
        });

        expectError(postJson(mockMvc, json), 400, code);
        verifyNoInteractions(service);
    }

    @Test
    void multipleErrors_shouldUsePrecedenceAndSortedMessage() throws Exception {
        String json = body(n -> {
            n.put("mobile", "bad");
            ((ObjectNode) n.get("address")).put("city", "C".repeat(26));
        });

        expectError(postJson(mockMvc, json), 400, "DFN002")
                .andExpect(jsonPath("$.errorMsg").value(org.hamcrest.Matchers.matchesPattern("address\\.city: .+; mobile: .+")));
    }

    @Test
    void serviceRuleFailure_shouldBe400WithItsCode() throws Exception {
        when(service.register(any())).thenThrow(new ApplicationException("address.country",
                "address.country must be an ISO 3166-1 alpha-2 code", "DFN001"));

        expectError(postJson(mockMvc, body(n -> { })), 400, "DFN001")
                .andExpect(jsonPath("$.errorMsg").value("address.country must be an ISO 3166-1 alpha-2 code"));
    }

    @Test
    void nonUuidCorrelationHeader_shouldYieldAFreshUuidNotA500() throws Exception {
        String correlationId = mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "not-a-uuid").content("{"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        String value = objectMapper.readTree(correlationId).get("correlationId").asText();
        assertThat(UUID.fromString(value)).isNotNull();
    }

    // ---------- end to end against a stubbed DFM ----------

    @ParameterizedTest(name = "DFM {0} -> {2} {3}")
    @CsvSource(delimiter = '|', value = {
            "200| {\"response_code\": \"200\", \"cust_nin\": \"N1\"}| 201| ",
            "400| {\"response_msg\": \"Request id should be a valid GUID.\"}| 400| DFN006",
            "409| {}| 409| DFN007",
            "500| {}| 502| DFN010",
            "401| {}| 502| DFN010",
            "200| not json| 502| DFN010"
    })
    void dfmOutcomes_shouldMapToStatusAndCode(int dfmStatus, String dfmBody, int status, String code) throws Exception {
        AtomicInteger exchanges = new AtomicInteger();
        ResultActions result = postJson(mvcAgainstDfm(PROPS, exchanges, dfm(dfmStatus, dfmBody)), body(n -> { }));

        if (status == 201) {
            result.andExpect(status().isCreated()).andExpect(jsonPath("$.response.nin").value("N1"));
        } else {
            expectError(result, status, code);
        }
        assertThat(exchanges).hasValue(1);
    }

    @Test
    void dfmRejection_shouldEchoDfmMessage() throws Exception {
        expectError(postJson(mvcAgainstDfm(PROPS, new AtomicInteger(), dfm(400, "{\"response_msg\": \"X\"}")), body(n -> { })), 400, "DFN006")
                .andExpect(jsonPath("$.errorMsg").value("X"));
    }

    @Test
    void dfmReadTimeout_shouldBe504() throws Exception {
        expectError(postJson(mvcAgainstDfm(new DfmMemberApiProperties("https://dfm.test", "/memberapis/onboarding", "TEST01", "header-key", "body-key", 5, 1, List.of()), new AtomicInteger(), Mono::never), body(n -> { })), 504, "DFN011");
    }

    @Test
    void dfmConnectFailure_shouldBeAttemptedTwiceThen504() throws Exception {
        AtomicInteger exchanges = new AtomicInteger();
        MockMvc mvc = mvcAgainstDfm(PROPS, exchanges, () -> Mono.error(new WebClientRequestException(
                new ConnectException("refused"), HttpMethod.POST, URI.create("https://dfm.test"), new HttpHeaders())));

        expectError(postJson(mvc, body(n -> { })), 504, "DFN011");
        assertThat(exchanges).hasValue(2);
    }

    @Test
    void sixthCallAfterFiveTechnicalFailures_shouldBe503WithoutOutboundRequest() throws Exception {
        AtomicInteger exchanges = new AtomicInteger();
        MockMvc mvc = mvcAgainstDfm(PROPS, exchanges, dfm(500, "{}"));
        for (int i = 0; i < 5; i++) {
            expectError(postJson(mvc, body(n -> { })), 502, "DFN010");
        }

        expectError(postJson(mvc, body(n -> { })), 503, "DFN014");
        assertThat(exchanges).hasValue(5);
    }

    @Test
    void blankSecurityKey_shouldBe500Dfn012WithoutOutboundRequest() throws Exception {
        AtomicInteger exchanges = new AtomicInteger();
        DfmMemberApiProperties blankKey = new DfmMemberApiProperties("https://dfm.test", "/memberapis/onboarding", "TEST01", "", "body-key", 5, 5, List.of());

        expectError(postJson(mvcAgainstDfm(blankKey, exchanges, dfm(200, OK_BODY)), body(n -> { })), 500, "DFN012")
                .andExpect(jsonPath("$.errorMsg").value("DFM integration is not configured"));
        assertThat(exchanges).hasValue(0);
    }
}
