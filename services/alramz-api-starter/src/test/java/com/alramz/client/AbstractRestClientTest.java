package com.alramz.client;

import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.exceptions.InvalidHttpRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractRestClientTest {

    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();

    static class TestClient extends AbstractRestClient {
        TestClient(WebClient webClient, Environment environment) {
            super(webClient, CircuitBreaker.ofDefaults("test"),
                    Retry.of("test", RetryConfig.custom().maxAttempts(1).build()),
                    TimeLimiter.of(Duration.ofSeconds(5)), environment, new ObjectMapper());
        }

        String post(String body) {
            return callSync(HttpMethod.POST, "/items", String.class, body, String.class);
        }

        String get() {
            return callSync(HttpMethod.GET, "/items", String.class);
        }

        String withHeaders(HttpHeaders headers, MultiValueMap<String, String> query) {
            return call(HttpMethod.GET, "/search", Void.class, null, String.class, headers, query).block();
        }

        String strict() {
            return call(HttpMethod.POST, "/strict", String.class, "x", String.class, true).block();
        }

        WebClient client() {
            return getWebClient();
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private TestClient client(HttpStatus status, String body, String... profiles) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://downstream")
                .exchangeFunction(request -> {
                    lastRequest.set(request);
                    return Mono.just(ClientResponse.create(status)
                            .header(HttpHeaders.CONTENT_TYPE, "application/json").body(body).build());
                })
                .build();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new TestClient(webClient, env);
    }

    private static void authenticateWithJwt() {
        Jwt jwt = Jwt.withTokenValue("jwt-token").header("alg", "none").subject("u").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));
    }

    @Test
    void sendsBearerTokenFromJwtPrincipalAndReturnsBody() {
        authenticateWithJwt();
        TestClient client = client(HttpStatus.OK, "\"done\"", "qa");

        assertThat(client.post("payload")).isEqualTo("\"done\"");
        assertThat(lastRequest.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(lastRequest.get().url().getPath()).isEqualTo("/items");
        assertThat(lastRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer jwt-token");
        assertThat(client.client()).isNotNull();
    }

    @Test
    void addsCustomHeadersAndQueryParams() {
        authenticateWithJwt();
        TestClient client = client(HttpStatus.OK, "ok");
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Custom", "1");
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("q", "abc");

        assertThat(client.withHeaders(headers, query)).isEqualTo("ok");
        assertThat(lastRequest.get().headers().getFirst("X-Custom")).isEqualTo("1");
        assertThat(lastRequest.get().url().getQuery()).isEqualTo("q=abc");
    }

    @Test
    void localProfileSkipsAuthorizationAndCustomHeaders() {
        authenticateWithJwt();
        TestClient client = client(HttpStatus.OK, "ok", "local");
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Custom", "1");

        client.withHeaders(headers, null);

        assertThat(lastRequest.get().headers().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(lastRequest.get().headers().containsHeader("X-Custom")).isFalse();
    }

    @Test
    void nonJwtPrincipalWithoutContextTokenSendsNoAuthorization() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));
        TestClient client = client(HttpStatus.OK, "ok");

        assertThat(client.get()).isEqualTo("ok");
        assertThat(lastRequest.get().headers().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void errorStatusMapsToApiCallFailedWithMessageFromBody() {
        authenticateWithJwt();
        TestClient client = client(HttpStatus.INTERNAL_SERVER_ERROR, "{\"message\":\"boom\"}");

        assertThatThrownBy(() -> client.post("x"))
                .isInstanceOfSatisfying(ApiCallFailedException.class, e -> {
                    assertThat(e.getResponseCode()).isEqualTo(500);
                    assertThat(e.getResponseMessage()).isEqualTo("boom");
                });
    }

    @Test
    void badRequestMapsToInvalidHttpRequestWhenRequested() {
        authenticateWithJwt();

        assertThatThrownBy(() -> client(HttpStatus.BAD_REQUEST, "{\"message\":\"bad field\"}").strict())
                .isInstanceOf(InvalidHttpRequestException.class).hasMessage("bad field");
        assertThatThrownBy(() -> client(HttpStatus.BAD_REQUEST, "not json").post("x"))
                .isInstanceOfSatisfying(ApiCallFailedException.class,
                        e -> assertThat(e.getResponseMessage()).isEqualTo("not json"));
    }
}
