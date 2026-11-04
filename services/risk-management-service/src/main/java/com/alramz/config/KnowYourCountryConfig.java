package com.alramz.config;

import com.alramz.exceptions.ApiCallFailedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Slf4j
@Configuration
@EnableConfigurationProperties(KnowYourCountryProperties.class)
@RequiredArgsConstructor
public class KnowYourCountryConfig {

    private static final String RESILIENCE_ID = "knowyourcountry";

    private final KnowYourCountryProperties properties;

    /**
     * The ApiKey filter is registered first so it runs before the logging/audit filter. It SETS the
     * header, replacing the inbound caller's bearer token that AbstractRestClient adds outside the
     * {@code local} profile, so the vendor gets exactly one Authorization value and never our JWT.
     */
    @Bean(name = "knowYourCountryWebClient")
    public WebClient knowYourCountryWebClient(WebClient.Builder builder,
                                              ExchangeFilterFunction alramzWebClientLoggingFilterFunction) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(apiKeyFilter(properties.getApiKey()))
                .filter(alramzWebClientLoggingFilterFunction)
                .build();
    }

    static ExchangeFilterFunction apiKeyFilter(String apiKey) {
        String headerValue = "ApiKey " + apiKey;
        String loggedValue = "ApiKey " + maskKey(apiKey);
        return (request, next) -> {
            log.info("KnowYourCountry request {} {} with header {}: {}",
                    request.method(), request.url(), HttpHeaders.AUTHORIZATION, loggedValue);
            return next.exchange(ClientRequest.from(request)
                    .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, headerValue))
                    .build());
        };
    }

    /** Never log the key itself: keep only the last 4 characters so the key in use can be identified. */
    static String maskKey(String apiKey) {
        return apiKey.length() <= 8 ? "****" : "****" + apiKey.substring(apiKey.length() - 4);
    }

    @Bean(name = "knowYourCountryCircuitBreaker")
    public CircuitBreaker knowYourCountryCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(RESILIENCE_ID);
    }

    /** One retry, only for connection failures and vendor 5xx; never for 4xx or an unreadable body. */
    @Bean(name = "knowYourCountryRetry")
    public Retry knowYourCountryRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2)
                .retryOnException(KnowYourCountryConfig::isRetryable)
                .build();
        return registry.retry(RESILIENCE_ID, config);
    }

    static boolean isRetryable(Throwable error) {
        return error instanceof WebClientRequestException
                || error instanceof ApiCallFailedException failed && failed.getResponseCode() >= 500;
    }

    @Bean(name = "knowYourCountryTimeLimiter")
    public TimeLimiter knowYourCountryTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(properties.getRequestTimeout())
                .build();
        return registry.timeLimiter(RESILIENCE_ID, config);
    }
}
