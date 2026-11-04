package com.alramz.knowyourcountry;

import com.alramz.config.KnowYourCountryConfig;
import com.alramz.config.KnowYourCountryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the real WebClient (built by {@link KnowYourCountryConfig}, so the real ApiKey filter and retry
 * predicate run) against a stubbed exchange instead of the live vendor.
 */
class KnowYourCountryClientTest {

    private static final String ROWS = """
            [{"name": "Afghanistan", "iso_alpha2": "AF", "iso_alpha3": "AFG", "risk_level": "Higher Risk", "score": 9.1},
             {"name": "Åland Islands", "iso_alpha2": "AX", "iso_alpha3": "ALA", "risk_level": "Lower Risk"}]
            """;

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();

    @BeforeEach
    void authenticateWithAnInboundJwt() {
        Jwt jwt = Jwt.withTokenValue("inbound-caller-token").header("alg", "none").subject("caller").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private KnowYourCountryClient client(Supplier<Mono<ClientResponse>> responses) {
        return client(responses, null, Duration.ofSeconds(2));
    }

    private KnowYourCountryClient client(Supplier<Mono<ClientResponse>> responses, CircuitBreaker breaker,
                                         Duration timeout) {
        KnowYourCountryProperties properties = new KnowYourCountryProperties();
        properties.setBaseUrl("https://knowyourcountry-test.example.invalid");
        properties.setApiKey("test-api-key");
        properties.setRequestTimeout(timeout);
        KnowYourCountryConfig config = new KnowYourCountryConfig(properties);

        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            sentRequest.set(request);
            return responses.get();
        });
        WebClient webClient = config.knowYourCountryWebClient(builder, (request, next) -> next.exchange(request));
        return new KnowYourCountryClient(
                webClient,
                breaker != null ? breaker : config.knowYourCountryCircuitBreaker(CircuitBreakerRegistry.ofDefaults()),
                config.knowYourCountryRetry(RetryRegistry.ofDefaults()),
                config.knowYourCountryTimeLimiter(TimeLimiterRegistry.ofDefaults()),
                new MockEnvironment().withProperty("spring.application.name", "risk-management-service"),
                new ObjectMapper());
    }

    private static Supplier<Mono<ClientResponse>> respond(HttpStatus status, String contentType, String body) {
        return () -> Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(body)
                .build());
    }

    private static Supplier<Mono<ClientResponse>> json(HttpStatus status, String body) {
        return respond(status, MediaType.APPLICATION_JSON_VALUE, body);
    }

    private static Supplier<Mono<ClientResponse>> connectionRefused() {
        return () -> Mono.error(new WebClientRequestException(new ConnectException("Connection refused"),
                HttpMethod.GET, URI.create("https://knowyourcountry-test.example.invalid"), new HttpHeaders()));
    }

    @Test
    void fetchCountries_shouldMapAVendorArrayInVendorOrder() {
        KnowYourCountryResult result = client(json(HttpStatus.OK, ROWS)).fetchCountries();

        assertThat(result).isInstanceOf(KnowYourCountryResult.Success.class);
        assertThat(((KnowYourCountryResult.Success) result).rows()).containsExactly(
                new KnowYourCountryRecord("Afghanistan", "AF", "AFG", "Higher Risk"),
                new KnowYourCountryRecord("Åland Islands", "AX", "ALA", "Lower Risk"));
        assertThat(sentRequest.get().url().getPath()).isEqualTo("/api/v4/countries_list");
        assertThat(sentRequest.get().method()).isEqualTo(HttpMethod.GET);
    }

    @Test
    void fetchCountryRatings_shouldCallTheRatingsPath() {
        KnowYourCountryResult result = client(json(HttpStatus.OK, ROWS)).fetchCountryRatings();

        assertThat(result).isInstanceOf(KnowYourCountryResult.Success.class);
        assertThat(sentRequest.get().url().getPath()).isEqualTo("/api/v4/country_ratings");
    }

    @Test
    void shouldSendOnlyTheApiKeyAndNeverForwardTheInboundBearer() {
        client(json(HttpStatus.OK, ROWS)).fetchCountries();

        List<String> authorization = sentRequest.get().headers().get(HttpHeaders.AUTHORIZATION);
        assertThat(authorization).containsExactly("ApiKey test-api-key");
        assertThat(sentRequest.get().headers().getFirst(HttpHeaders.ACCEPT)).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void shouldReturnAnEmptySuccessForAnEmptyArray() {
        KnowYourCountryResult result = client(json(HttpStatus.OK, "[]")).fetchCountries();

        assertThat(result).isEqualTo(new KnowYourCountryResult.Success(List.of()));
    }

    @Test
    void shouldDropNullArrayElements() {
        String body = """
                [null, {"name": "Albania", "iso_alpha2": "AL", "iso_alpha3": "ALB", "risk_level": "Medium Risk"}, null]
                """;

        KnowYourCountryResult result = client(json(HttpStatus.OK, body)).fetchCountryRatings();

        assertThat(((KnowYourCountryResult.Success) result).rows())
                .containsExactly(new KnowYourCountryRecord("Albania", "AL", "ALB", "Medium Risk"));
    }

    @Test
    void shouldReportRejectedOnA4xxWithoutRetrying() {
        KnowYourCountryResult result = client(json(HttpStatus.UNAUTHORIZED, "{\"message\": \"bad key\"}"))
                .fetchCountries();

        assertThat(result).isEqualTo(new KnowYourCountryResult.Rejected(401));
        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldRetryA5xxOnceThenReportRejected() {
        KnowYourCountryResult result = client(json(HttpStatus.SERVICE_UNAVAILABLE, "{\"message\": \"down\"}"))
                .fetchCountries();

        assertThat(result).isEqualTo(new KnowYourCountryResult.Rejected(503));
        assertThat(calls).hasValue(2);
    }

    @Test
    void shouldReportMalformedForInvalidJsonWithoutRetrying() {
        KnowYourCountryResult result = client(json(HttpStatus.OK, "[{not json")).fetchCountries();

        assertThat(result).isInstanceOf(KnowYourCountryResult.Malformed.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldReportMalformedForAJsonObjectInsteadOfAnArray() {
        KnowYourCountryResult result = client(json(HttpStatus.OK, "{\"countries\": []}")).fetchCountries();

        assertThat(result).isInstanceOf(KnowYourCountryResult.Malformed.class);
    }

    @Test
    void shouldReportMalformedForAnEmptyBody() {
        KnowYourCountryResult result = client(json(HttpStatus.OK, "")).fetchCountries();

        assertThat(result).isInstanceOf(KnowYourCountryResult.Malformed.class);
    }

    @Test
    void shouldReportMalformedForAnHtmlBody() {
        KnowYourCountryResult result = client(respond(HttpStatus.OK, MediaType.TEXT_HTML_VALUE, "<html>proxy</html>"))
                .fetchCountries();

        assertThat(result).isInstanceOf(KnowYourCountryResult.Malformed.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldRetryAConnectionErrorExactlyOnceThenReportUnavailable() {
        KnowYourCountryResult result = client(connectionRefused()).fetchCountries();

        assertThat(result).isInstanceOf(KnowYourCountryResult.Unavailable.class);
        assertThat(((KnowYourCountryResult.Unavailable) result).cause()).isInstanceOf(WebClientRequestException.class);
        assertThat(calls).hasValue(2);
    }

    @Test
    void shouldReportUnavailableOnTimeout() {
        KnowYourCountryResult result = client(Mono::never, null, Duration.ofMillis(200)).fetchCountries();

        assertThat(result).isInstanceOf(KnowYourCountryResult.Unavailable.class);
    }

    @Test
    void shouldReportUnavailableWhenTheCircuitIsOpen() {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults("knowyourcountry-test");
        breaker.transitionToForcedOpenState();

        KnowYourCountryResult result = client(json(HttpStatus.OK, ROWS), breaker, Duration.ofSeconds(2))
                .fetchCountries();

        assertThat(result).isInstanceOf(KnowYourCountryResult.Unavailable.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void shouldRethrowAnUnexpectedFailure() {
        KnowYourCountryClient client = client(() -> Mono.error(new IllegalStateException("bug")));

        assertThatThrownBy(client::fetchCountries).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void success_shouldDefensivelyCopyItsRows() {
        List<KnowYourCountryRecord> rows = new java.util.ArrayList<>();
        rows.add(new KnowYourCountryRecord("A", "AA", "AAA", "Lower Risk"));
        KnowYourCountryResult.Success success = new KnowYourCountryResult.Success(rows);
        rows.clear();

        List<KnowYourCountryRecord> copy = success.rows();
        assertThat(copy).hasSize(1);
        assertThatThrownBy(copy::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
