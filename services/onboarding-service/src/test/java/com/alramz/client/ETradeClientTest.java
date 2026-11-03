package com.alramz.client;

import com.alramz.config.JacksonConfig;
import com.alramz.exception.ExternalSystemException;
import com.alramz.model.ETradeResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
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
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ETradeClientTest {

    private final AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();

    @BeforeEach
    void authenticate() {
        // Non-local profile: the base client resolves the caller from the security context before adding custom headers.
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("svc-user", null));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ETradeClient client(Mono<ClientResponse> answer) {
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            sentRequest.set(request);
            return answer;
        }).build();
        return new ETradeClient(
                webClient,
                CircuitBreaker.ofDefaults("etrade"),
                Retry.of("etrade", RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiter.ofDefaults("etrade"),
                new MockEnvironment(),
                new JacksonConfig().objectMapper());
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    @Test
    void callETrade_shouldSendAccessTokenAndParseResponse() {
        ETradeClient client = client(json(HttpStatus.OK, "{\"Error_code\": \"0\", \"Reference_No\": \"R1\", \"Exists\": true}"));

        ETradeResponse response = client.callETrade("tok-123", "/validate/email", "POST", Map.of("email", "a@b.c"), ETradeResponse.class);

        assertThat(response.errorCode()).isEqualTo("0");
        assertThat(response.referenceNo()).isEqualTo("R1");
        assertThat(response.exists()).isTrue();
        assertThat(sentRequest.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(sentRequest.get().url().getPath()).isEqualTo("/validate/email");
        assertThat(sentRequest.get().headers().getFirst("access-token")).isEqualTo("tok-123");
    }

    @Test
    void callETrade_shouldWrapHttpErrorAsExternalSystemException() {
        ETradeClient client = client(json(HttpStatus.SERVICE_UNAVAILABLE, "{\"message\": \"maintenance\"}"));

        assertThatThrownBy(() -> client.callETrade("tok-123", "/validate/email", "POST", Map.of(), ETradeResponse.class))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessageStartingWith("eTrade validation service unavailable: ");
    }

    @Test
    void callETrade_shouldWrapTransportFailureAsExternalSystemException() {
        ETradeClient client = client(Mono.error(new IllegalStateException("connection refused")));

        assertThatThrownBy(() -> client.callETrade("tok-123", "/validate/email", "GET", null, ETradeResponse.class))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessageContaining("connection refused");
    }
}
