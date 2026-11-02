package com.alramz.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ETradeTokenProviderTest {

    private static final ETradeProperties PROPS = new ETradeProperties(
            "https://etrade.test", "client-id", "client-secret", null, "5s", 3000, "/token", Map.of());

    private static final String TOKEN_OK = """
            {"Error_code": "0", "resData": {"access_token": "tok-123", "expires_in": 3600}}
            """;

    private final AtomicInteger exchanges = new AtomicInteger();
    private final AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();

    private ETradeTokenProvider provider(Supplier<Mono<ClientResponse>> answer) {
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            exchanges.incrementAndGet();
            sentRequest.set(request);
            return answer.get();
        }).exchangeStrategies(STRATEGIES).build();
        return new ETradeTokenProvider(webClient, PROPS, new JacksonConfig().objectMapper());
    }

    /** Same codec setup as ExternalETradeServiceConfig: the provider reads Jackson 2 {@code JsonNode}. */
    private static final ExchangeStrategies STRATEGIES = ExchangeStrategies.builder()
            .codecs(c -> c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(new JacksonConfig().objectMapper())))
            .build();

    private static Supplier<Mono<ClientResponse>> json(HttpStatus status, String body) {
        return () -> Mono.just(ClientResponse.create(status, STRATEGIES)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    @Test
    void getToken_shouldFetchTokenOnceAndServeFromCacheAfterwards() {
        ETradeTokenProvider provider = provider(json(HttpStatus.OK, TOKEN_OK));

        assertThat(provider.getToken()).contains("tok-123");
        assertThat(provider.getToken()).contains("tok-123");

        assertThat(exchanges).hasValue(1);
        assertThat(sentRequest.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(sentRequest.get().url().getPath()).isEqualTo("/token");
        assertThat(sentRequest.get().headers().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
    }

    @Test
    void invalidate_shouldForceRefetchOnNextCall() {
        ETradeTokenProvider provider = provider(json(HttpStatus.OK, TOKEN_OK));
        provider.getToken();

        provider.invalidate();

        assertThat(provider.getToken()).contains("tok-123");
        assertThat(exchanges).hasValue(2);
    }

    @Test
    void getToken_shouldReturnEmptyWhenProviderReportsErrorCode() {
        ETradeTokenProvider provider = provider(json(HttpStatus.OK, "{\"Error_code\": \"101\"}"));

        assertThat(provider.getToken()).isEmpty();
    }

    @Test
    void getToken_shouldReturnEmptyWhenAccessTokenIsMissing() {
        ETradeTokenProvider provider = provider(json(HttpStatus.OK, "{\"Error_code\": \"0\", \"resData\": {\"access_token\": \"\"}}"));

        assertThat(provider.getToken()).isEmpty();
    }

    @Test
    void getToken_shouldNotCacheFailedFetch() {
        ETradeTokenProvider provider = provider(json(HttpStatus.OK, "{\"Error_code\": \"0\", \"resData\": {}}"));

        assertThat(provider.getToken()).isEmpty();
        assertThat(provider.getToken()).isEmpty();
        assertThat(exchanges).hasValue(2);
    }

    @Test
    void getToken_shouldReturnEmptyOnHttpError() {
        ETradeTokenProvider provider = provider(json(HttpStatus.UNAUTHORIZED, "{\"message\": \"bad client\"}"));

        assertThat(provider.getToken()).isEmpty();
    }

    @Test
    void getToken_shouldReturnEmptyWhenResponseBodyIsEmpty() {
        ETradeTokenProvider provider = provider(() -> Mono.just(ClientResponse.create(HttpStatus.OK, STRATEGIES).build()));

        assertThat(provider.getToken()).isEmpty();
    }

    @Test
    void getToken_shouldReturnEmptyOnTransportFailure() {
        ETradeTokenProvider provider = provider(() -> Mono.error(new IllegalStateException("connection refused")));

        assertThat(provider.getToken()).isEmpty();
    }
}
