package com.alramz.dfm;

import com.alramz.config.DfmMemberApiProperties;
import com.alramz.config.ExternalDfmMemberApiConfig;
import com.alramz.config.JacksonConfig;
import com.alramz.exception.DfmConfigurationException;
import com.alramz.model.DfmAddress;
import com.alramz.model.DfmCrs;
import com.alramz.model.DfmCrsCountry;
import com.alramz.model.DfmDividendPayment;
import com.alramz.model.DfmFatca;
import com.alramz.model.DfmGuardian;
import com.alramz.model.DfmInvestorRegistrationRequest;
import com.alramz.model.DfmPassport;
import com.alramz.model.DfmPortfolioOption;
import com.alramz.service.impl.DfmInvestorServiceImplTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Drives the real WebClient + the resilience chain built by ExternalDfmMemberApiConfig against a stubbed exchange. */
class DfmMemberApiClientTest {

    private static final DfmMemberApiProperties PROPS =
            new DfmMemberApiProperties("https://dfm.test", "/memberapis/onboarding", "TEST01", "header-key", "body-key", 5, 5, List.of("DUP1"));
    /** Same, with a 1 s read timeout for the timeout test only (the first call in a JVM can take over 1 s). */
    private static final DfmMemberApiProperties FAST_TIMEOUT =
            new DfmMemberApiProperties("https://dfm.test", "/memberapis/onboarding", "TEST01", "header-key", "body-key", 5, 1, List.of("DUP1"));

    private static final String OK_BODY = """
            {"response_code": "200", "response_msg": "OK", "member_reference": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
             "dfm_reference": "1b2c3d4e-5f60-4718-89ab-cdef01234567", "tracking_no": "2313258142",
             "cash_acc": "9400000", "margin_acc": null, "cust_nin": "EGA0000000", "extra": 1}
            """;

    private final AtomicInteger exchanges = new AtomicInteger();
    private final AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();
    private final AtomicReference<String> sentBody = new AtomicReference<>();
    private final CircuitBreaker circuitBreaker =
            new ExternalDfmMemberApiConfig(PROPS).dfmMemberApiCircuitBreaker(CircuitBreakerRegistry.ofDefaults());

    private DfmMemberApiClient client(DfmMemberApiProperties props, Supplier<Mono<ClientResponse>> answer) {
        ExternalDfmMemberApiConfig config = new ExternalDfmMemberApiConfig(props);
        // Non-blocking body capture: the retry resubscribes on Reactor's parallel scheduler, where block() is illegal.
        ExchangeFunction exchange = request -> {
            exchanges.incrementAndGet();
            sentRequest.set(request);
            return bodyOf(request).doOnNext(sentBody::set).then(Mono.defer(answer::get));
        };
        return new DfmMemberApiClient(
                WebClient.builder().exchangeFunction(exchange).build(),
                circuitBreaker,
                config.dfmMemberApiRetry(RetryRegistry.ofDefaults()),
                config.dfmMemberApiTimeLimiter(TimeLimiterRegistry.ofDefaults()),
                props,
                new JacksonConfig().objectMapper());
    }

    private DfmMemberApiClient client(Supplier<Mono<ClientResponse>> answer) {
        return client(PROPS, answer);
    }

    private static Supplier<Mono<ClientResponse>> json(HttpStatus status, String body) {
        return () -> Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private static Supplier<Mono<ClientResponse>> empty(HttpStatus status) {
        return () -> Mono.just(ClientResponse.create(status).build());
    }

    private static Mono<String> bodyOf(ClientRequest request) {
        MockClientHttpRequest mock = new MockClientHttpRequest(HttpMethod.POST, "/");
        return request.body().insert(mock, new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Map.of();
            }
        }).then(Mono.defer(mock::getBodyAsString));
    }

    private static DfmInvestorRegistrationRequest request() {
        return DfmInvestorServiceImplTest.validRequest();
    }

    private static WebClientRequestException requestFailure(Throwable cause) {
        return new WebClientRequestException(cause, HttpMethod.POST, URI.create("https://dfm.test/memberapis/onboarding"), new HttpHeaders());
    }

    // ---------- outbound request ----------

    @Test
    void onboard_shouldSendTheMappedSnakeCasePayloadWithKeyFromConfig() throws Exception {
        String line = "1234567890123456789012345678901234567890";
        DfmInvestorRegistrationRequest request = request()
                .portfolioOptions(new LinkedHashSet<>(List.of(DfmPortfolioOption.MARGIN, DfmPortfolioOption.CASH)))
                .fatca(new DfmFatca().usPerson(false))
                .crs(new DfmCrs().resident(true).countries(List.of(new DfmCrsCountry().country("AE").hasTin(false).noTinReason("B"))));
        request.getAddress().line(line).phone("+97140000000");

        client(json(HttpStatus.OK, OK_BODY)).onboard(request);

        ClientRequest sent = sentRequest.get();
        assertThat(sent.method()).isEqualTo(HttpMethod.POST);
        assertThat(sent.url()).hasToString("https://dfm.test/memberapis/onboarding");
        assertThat(sent.headers().getFirst("securitykey")).isEqualTo("header-key");
        assertThat(sent.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(sent.headers().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
        assertThat(sent.headers().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();

        JsonNode body = new ObjectMapper().readTree(sentBody.get());
        assertThat(body.get("request_id").asText()).isEqualTo("3f2b8c1e-7d4a-4f6b-9c2e-1a0b9d8e7f60");
        assertThat(body.get("securitykey").asText()).isEqualTo("body-key");
        assertThat(body.get("request_mbrcode").asText()).isEqualTo("TEST01");
        assertThat(body.get("cust_firstname").asText()).isEqualTo("Test");
        assertThat(body.get("cust_engfullname").asText()).isEqualTo("Test User");
        assertThat(body.get("cust_dob").asText()).isEqualTo("1990-01-15");
        assertThat(body.get("cust_gender").asText()).isEqualTo("M");
        assertThat(body.get("cust_mobile").asText()).isEqualTo("00971500000000");
        assertThat(body.get("adr_phone").asText()).isEqualTo("0097140000000");
        assertThat(body.get("adr_address").asText()).isEqualTo(line);
        assertThat(body.get("adr_pobox").asText()).isEqualTo("00000");
        assertThat(body.get("portfolio_options").asText()).isEqualTo("cash,margin");
        assertThat(body.get("pay_method").asText()).isEqualTo("bank");
        assertThat(body.get("pay_aed_iban").asText()).isEqualTo("AE000000000000000000000");
        assertThat(body.get("fatca_uscitizen").asText()).isEqualTo("N");
        assertThat(body.get("csr_resident").asText()).isEqualTo("Y");
        assertThat(body.at("/csr/csr_countries/0/csr_country").asText()).isEqualTo("AE");
        assertThat(body.at("/csr/csr_countries/0/csr_havetin").asText()).isEqualTo("N");
        assertThat(body.at("/csr/csr_countries/0/csr_notinreason").asText()).isEqualTo("B");
        assertThat(body.get("eid_no").asText()).isEqualTo("784-0000-0000000-0");
        assertThat(body.get("eid_expirydate").asText()).isEqualTo("2030-01-01");
        assertThat(body.get("eid_attachment_front").asText()).isEqualTo(DfmInvestorServiceImplTest.PNG);
        assertThat(body.get("cust_signatureimage_type").asText()).isEqualTo("png");
        // nulls are omitted, not sent as null
        assertThat(body.has("cust_familyid")).isFalse();
        assertThat(body.has("pp_no")).isFalse();
        assertThat(body.has("fatca_tin")).isFalse();
        assertThat(body.has("guardian_name")).isFalse();
        // key first, attachments last (keeps the key inside the masked prefix when spans truncate)
        assertThat(sentBody.get().indexOf("\"securitykey\"")).isLessThan(sentBody.get().indexOf("\"eid_attachment_front\""));
    }

    @Test
    void onboard_shouldMapPassportGuardianAndIvestor() throws Exception {
        DfmInvestorRegistrationRequest request = request()
                .emiratesId(null)
                .passport(new DfmPassport().passportNumber("X0000000").expiryDate(LocalDate.of(2031, 2, 3))
                        .imageType(DfmPassport.ImageTypeEnum.JPEG).image(DfmInvestorServiceImplTest.JPEG))
                .guardian(new DfmGuardian().name("Guardian Name").type("PARENT").proofDocument(DfmInvestorServiceImplTest.PDF))
                .dividendPayment(new DfmDividendPayment().method(DfmDividendPayment.MethodEnum.IVESTOR).ivestorPrintName("TEST USER"))
                .fatca(new DfmFatca().usPerson(true).tin("123"))
                .crs(new DfmCrs())
                .gender(DfmInvestorRegistrationRequest.GenderEnum.F);

        client(json(HttpStatus.OK, OK_BODY)).onboard(request);

        JsonNode body = new ObjectMapper().readTree(sentBody.get());
        assertThat(body.get("pay_method").asText()).isEqualTo("iVESTOR");
        assertThat(body.get("pay_printname").asText()).isEqualTo("TEST USER");
        assertThat(body.get("portfolio_options").asText()).isEqualTo("cash");
        assertThat(body.get("pp_no").asText()).isEqualTo("X0000000");
        assertThat(body.get("pp_expirydate").asText()).isEqualTo("2031-02-03");
        assertThat(body.get("pp_attachment_type").asText()).isEqualTo("jpeg");
        assertThat(body.get("guardian_name").asText()).isEqualTo("Guardian Name");
        assertThat(body.get("guardianship_type").asText()).isEqualTo("PARENT");
        assertThat(body.get("proof_of_guardianship").asText()).isEqualTo(DfmInvestorServiceImplTest.PDF);
        assertThat(body.get("fatca_uscitizen").asText()).isEqualTo("Y");
        assertThat(body.get("fatca_tin").asText()).isEqualTo("123");
        assertThat(body.get("cust_gender").asText()).isEqualTo("F");
        assertThat(body.has("eid_no")).isFalse();
        assertThat(body.has("csr")).isFalse();
        assertThat(body.has("csr_resident")).isFalse();
    }

    // ---------- classification ----------

    @Test
    void http200WithCode200_shouldBeSuccess() {
        DfmOnboardingOutcome outcome = client(json(HttpStatus.OK, OK_BODY)).onboard(request());

        assertThat(outcome).isInstanceOf(DfmOnboardingOutcome.Success.class);
        DfmOnboardingResponse body = ((DfmOnboardingOutcome.Success) outcome).body();
        assertThat(body.custNin()).isEqualTo("EGA0000000");
        assertThat(body.cashAcc()).isEqualTo("9400000");
        assertThat(body.trackingNo()).isEqualTo("2313258142");
        assertThat(body.memberReference()).isEqualTo("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");
        assertThat(body.dfmReference()).isEqualTo("1b2c3d4e-5f60-4718-89ab-cdef01234567");
        assertThat(body.responseMsg()).isEqualTo("OK");
    }

    @Test
    void numericResponseCode_shouldBeCoerced() {
        DfmOnboardingOutcome outcome = client(json(HttpStatus.OK, "{\"response_code\": 200, \"cust_nin\": \"N1\"}")).onboard(request());

        assertThat(outcome).isInstanceOf(DfmOnboardingOutcome.Success.class);
        assertThat(((DfmOnboardingOutcome.Success) outcome).body().responseCode()).isEqualTo("200");
    }

    @Test
    void http400WithMessage_shouldBeRejectedWithThatMessage() {
        DfmOnboardingOutcome outcome = client(json(HttpStatus.BAD_REQUEST,
                "{\"response_code\": \"400\", \"response_msg\": \"Request id should be a valid GUID.\"}")).onboard(request());

        assertThat(outcome).isEqualTo(new DfmOnboardingOutcome.Rejected("400", "Request id should be a valid GUID.", false));
    }

    @Test
    void http422WithUnreadableBody_shouldBeRejectedWithHttpStatusAsCode() {
        DfmOnboardingOutcome outcome = client(json(HttpStatus.valueOf(422), "<html>nope</html>")).onboard(request());

        assertThat(outcome).isEqualTo(new DfmOnboardingOutcome.Rejected("422", null, false));
    }

    @Test
    void http400WithEmptyBody_shouldBeRejected() {
        assertThat(client(empty(HttpStatus.BAD_REQUEST)).onboard(request()))
                .isEqualTo(new DfmOnboardingOutcome.Rejected("400", null, false));
    }

    @Test
    void http409_shouldBeDuplicateInProgress() {
        DfmOnboardingOutcome outcome = client(json(HttpStatus.CONFLICT, "{\"response_msg\": \"busy\"}")).onboard(request());

        assertThat(outcome).isEqualTo(new DfmOnboardingOutcome.Rejected("409", "busy", true));
    }

    @Test
    void http200WithConfiguredDuplicateCode_shouldBeDuplicateInProgress() {
        DfmOnboardingOutcome outcome = client(json(HttpStatus.OK, "{\"response_code\": \"DUP1\", \"response_msg\": \"wait\"}")).onboard(request());

        assertThat(outcome).isEqualTo(new DfmOnboardingOutcome.Rejected("DUP1", "wait", true));
    }

    @Test
    void http200WithResponseCode4xx_shouldBeRejected() {
        DfmOnboardingOutcome outcome = client(json(HttpStatus.OK, "{\"response_code\": \"404\", \"response_msg\": \"bad\"}")).onboard(request());

        assertThat(outcome).isEqualTo(new DfmOnboardingOutcome.Rejected("404", "bad", false));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "200| {\"response_code\": \"500\"}",
            "200| {\"response_code\": \"abc\"}",
            "200| {\"response_msg\": \"no code\"}",
            "200| <html>not json</html>",
            "200| null",
            "500| {\"response_msg\": \"boom\"}",
            "502| ''",
            "401| {}",
            "403| {}",
            "302| {}"
    })
    void technicalFailures_shouldBeBadResponseAfterExactlyOneAttempt(int status, String body) {
        DfmOnboardingOutcome outcome = client(json(HttpStatus.valueOf(status), body)).onboard(request());

        assertThat(outcome).isInstanceOf(DfmOnboardingOutcome.Unavailable.class);
        assertThat(((DfmOnboardingOutcome.Unavailable) outcome).reason()).isEqualTo(DfmOnboardingOutcome.Reason.BAD_RESPONSE);
        assertThat(exchanges).hasValue(1);
    }

    @Test
    void http200WithEmptyBody_shouldBeBadResponse() {
        DfmOnboardingOutcome outcome = client(empty(HttpStatus.OK)).onboard(request());

        assertThat(((DfmOnboardingOutcome.Unavailable) outcome).reason()).isEqualTo(DfmOnboardingOutcome.Reason.BAD_RESPONSE);
    }

    @Test
    void readTimeout_shouldBeTimeoutWithoutRetry() {
        DfmOnboardingOutcome outcome = client(FAST_TIMEOUT, Mono::never).onboard(request());

        assertThat(((DfmOnboardingOutcome.Unavailable) outcome).reason()).isEqualTo(DfmOnboardingOutcome.Reason.TIMEOUT);
        assertThat(exchanges).hasValue(1);
    }

    @Test
    void connectRefused_shouldBeRetriedOnceThenTimeout() {
        DfmOnboardingOutcome outcome = client(() -> Mono.error(requestFailure(new ConnectException("refused")))).onboard(request());

        assertThat(((DfmOnboardingOutcome.Unavailable) outcome).reason()).isEqualTo(DfmOnboardingOutcome.Reason.TIMEOUT);
        assertThat(exchanges).hasValue(2);
    }

    @Test
    void connectTimeout_shouldBeRetriedOnceThenTimeout() {
        DfmOnboardingOutcome outcome = client(() -> Mono.error(requestFailure(new HttpConnectTimeoutException("slow")))).onboard(request());

        assertThat(((DfmOnboardingOutcome.Unavailable) outcome).reason()).isEqualTo(DfmOnboardingOutcome.Reason.TIMEOUT);
        assertThat(exchanges).hasValue(2);
    }

    @Test
    void connectRefusedThenSuccess_shouldSucceedOnTheRetry() {
        AtomicInteger calls = new AtomicInteger();
        DfmOnboardingOutcome outcome = client(() -> calls.incrementAndGet() == 1
                ? Mono.error(requestFailure(new ConnectException("refused")))
                : json(HttpStatus.OK, OK_BODY).get()).onboard(request());

        assertThat(outcome).isInstanceOf(DfmOnboardingOutcome.Success.class);
        assertThat(exchanges).hasValue(2);
    }

    @Test
    void otherIoFailure_shouldBeBadResponseWithoutRetry() {
        DfmOnboardingOutcome outcome = client(() -> Mono.error(requestFailure(new IOException("reset")))).onboard(request());

        assertThat(((DfmOnboardingOutcome.Unavailable) outcome).reason()).isEqualTo(DfmOnboardingOutcome.Reason.BAD_RESPONSE);
        assertThat(exchanges).hasValue(1);
    }

    // ---------- circuit breaker ----------

    @Test
    void fiveTechnicalFailures_shouldOpenTheBreakerAndShortCircuitTheSixthCall() {
        DfmMemberApiClient client = client(json(HttpStatus.INTERNAL_SERVER_ERROR, "{}"));
        for (int i = 0; i < 5; i++) {
            client.onboard(request());
        }

        DfmOnboardingOutcome sixth = client.onboard(request());

        assertThat(((DfmOnboardingOutcome.Unavailable) sixth).reason()).isEqualTo(DfmOnboardingOutcome.Reason.CIRCUIT_OPEN);
        assertThat(exchanges).hasValue(5);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void businessRejections_shouldNotOpenTheBreaker() {
        DfmMemberApiClient client = client(json(HttpStatus.BAD_REQUEST, "{\"response_msg\": \"no\"}"));
        for (int i = 0; i < 6; i++) {
            assertThat(client.onboard(request())).isInstanceOf(DfmOnboardingOutcome.Rejected.class);
        }

        assertThat(exchanges).hasValue(6);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ---------- configuration ----------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''| TEST01| header-key| body-key| adapter.dfm-member-api.base-url",
            "http://dfm.test| TEST01| header-key| body-key| adapter.dfm-member-api.base-url",
            "https://dfm.test| ' '| header-key| body-key| adapter.dfm-member-api.member-code",
            "https://dfm.test| TEST01| ''| body-key| adapter.dfm-member-api.header-security-key",
            "https://dfm.test| TEST01| header-key| ''| adapter.dfm-member-api.body-security-key"
    })
    void missingOrInsecureConfig_shouldFailBeforeAnyRequest(String baseUrl, String memberCode, String headerKey, String bodyKey, String expectedKey) {
        DfmMemberApiProperties props = new DfmMemberApiProperties(baseUrl, "/memberapis/onboarding", memberCode, headerKey, bodyKey, 5, 5, List.of());

        assertThatThrownBy(() -> client(props, json(HttpStatus.OK, OK_BODY)).onboard(request()))
                .isInstanceOf(DfmConfigurationException.class)
                .hasMessageContaining(expectedKey)
                .hasMessageNotContaining("header-key")
                .hasMessageNotContaining("body-key");
        assertThat(exchanges).hasValue(0);
    }

    @Test
    void nullConfig_shouldFailBeforeAnyRequest() {
        DfmMemberApiProperties props = new DfmMemberApiProperties(null, null, null, null, null, 5, 5, null);

        assertThatThrownBy(() -> client(props, json(HttpStatus.OK, OK_BODY)).onboard(request()))
                .isInstanceOf(DfmConfigurationException.class);
        assertThat(exchanges).hasValue(0);
    }

    @Test
    void nullDuplicateCodes_shouldStillClassify() {
        DfmMemberApiProperties props = new DfmMemberApiProperties("https://dfm.test", "/memberapis/onboarding", "TEST01", "header-key", "body-key", 5, 5, null);

        assertThat(client(props, json(HttpStatus.OK, OK_BODY)).onboard(request())).isInstanceOf(DfmOnboardingOutcome.Success.class);
    }

    // ---------- isConnectFailure ----------

    @Test
    void isConnectFailure_shouldWalkTheCauseChain() {
        assertThat(DfmMemberApiClient.isConnectFailure(requestFailure(new ConnectException("x")))).isTrue();
        assertThat(DfmMemberApiClient.isConnectFailure(new RuntimeException(requestFailure(new HttpConnectTimeoutException("x"))))).isTrue();
        assertThat(DfmMemberApiClient.isConnectFailure(requestFailure(new IOException("x")))).isFalse();
        assertThat(DfmMemberApiClient.isConnectFailure(null)).isFalse();
    }
}
