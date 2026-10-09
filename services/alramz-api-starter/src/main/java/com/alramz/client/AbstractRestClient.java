package com.alramz.client;

import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.exceptions.InvalidHttpRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;
import com.fasterxml.jackson.core.JsonProcessingException;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractRestClient {
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    protected <RQ, RS> Mono<RS> call(HttpMethod method, String uri, Class<RQ> requestType, RQ request, Class<RS> responseType) {
        return execute(method, uri, requestType, request, responseType, null, null, false);
    }

    protected <RQ, RS> Mono<RS> call(HttpMethod method, String uri, Class<RQ> requestType, RQ request, Class<RS> responseType, HttpHeaders headers, MultiValueMap<String, String> queryParams) {
        return execute(method, uri, requestType, request, responseType, headers, queryParams, false);
    }

    protected <RQ, RS> Mono<RS> call(HttpMethod method, String uri, Class<RQ> requestType, RQ request, Class<RS> responseType, boolean handleBadRequest) {
        return execute(method, uri, requestType, request, responseType, null, null, handleBadRequest);
    }

    private <RQ, RS> Mono<RS> execute(HttpMethod method, String uri, Class<RQ> requestType, RQ request, Class<RS> responseType, HttpHeaders customHeaders, MultiValueMap<String, String> queryParams, boolean handleBadRequest) {
        log.info("Calling [{} {}]", method, uri);
        return webClient.method(method).uri(builder -> {
                    builder.path(uri);
                    if (queryParams != null) {
                        builder.queryParams(queryParams);
                    }
                    return builder.build();
                }).headers(headers -> {
                    if (!isLocalProfile()) {
                        String token = getToken();
                        if (token != null) {
                            headers.setBearerAuth(token);
                        }
                        if (customHeaders != null) {
                            headers.addAll(customHeaders);
                        }
                    }
                }).body(Mono.justOrEmpty(request), requestType).retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class).flatMap(body -> handleError(uri, method, response.statusCode(), body, handleBadRequest)))
                .bodyToMono(responseType)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .doOnSuccess(response -> log.info("API call successful [{} {}]", method, uri))
                .doOnError(error -> log.error("API call failed [{} {}]", method, uri, error));
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles())
                .contains("local");
    }

    private Mono<? extends Throwable> handleError(String uri, HttpMethod method, HttpStatusCode status, String body, boolean handleBadRequest) {
        String message = extractMessage(body);
        if (handleBadRequest && status.equals(HttpStatus.BAD_REQUEST)) {
            return Mono.error(new InvalidHttpRequestException(message));
        }
        return Mono.error(new ApiCallFailedException(uri, method.name(), status.value(), message));
    }

    private String extractMessage(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.path("message").asText("");
        } catch (JsonProcessingException ex) {
            log.warn("Unable to parse error response", ex);
            return body;
        }
    }

    protected <RQ, RS> RS callSync(HttpMethod method, String uri, Class<RQ> requestType, RQ request, Class<RS> responseType) {
        return call(method, uri, requestType, request, responseType).block();
    }

    protected <RS> RS callSync(HttpMethod method, String uri, Class<RS> responseType) {
        return call(method, uri, Void.class, null, responseType).block();
    }

    protected WebClient getWebClient() {
        return webClient;
    }

    private String getToken() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication()).filter(auth -> auth.getPrincipal() instanceof Jwt).map(auth -> ((Jwt) auth.getPrincipal()).getTokenValue()).orElseGet(this::getContextToken);
    }

    private String getContextToken() {
        String token = UserRequestContext.get().getBearerToken();
        if (token == null) {
            return null;
        }
        return token.replace("Bearer ", "");
    }
}