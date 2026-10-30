package com.alramz.config;

import com.alramz.dfm.DfmMemberApiClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * WebClient and resilience beans for the DFM Member API. This service has no reactor-netty on its classpath
 * (verified with dependency:tree), so the connect timeout is set on the JDK connector; the read timeout is the
 * TimeLimiter alone. No baseUrl on the builder: the client validates config at call time instead of at startup.
 */
@Configuration
@EnableConfigurationProperties(DfmMemberApiProperties.class)
@RequiredArgsConstructor
public class ExternalDfmMemberApiConfig {

    private static final String RESILIENCE_ID = "dfm-member-api";

    private final DfmMemberApiProperties properties;

    @Bean(name = "dfmMemberApiWebClient")
    public WebClient dfmMemberApiWebClient(WebClient.Builder builder, ExchangeFilterFunction alramzWebClientLoggingFilterFunction) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .build();
        return builder
                .clientConnector(new JdkClientHttpConnector(httpClient))
                .filter(alramzWebClientLoggingFilterFunction)
                .build();
    }

    /** Opens after 5 consecutive technical failures; business rejections are successes for the breaker. */
    @Bean(name = "dfmMemberApiCircuitBreaker")
    public CircuitBreaker dfmMemberApiCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(100)
                .build();
        return registry.circuitBreaker(RESILIENCE_ID, config);
    }

    /** One retry, only when DFM was never reached; a read timeout is never resent. */
    @Bean(name = "dfmMemberApiRetry")
    public Retry dfmMemberApiRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2)
                .retryOnException(DfmMemberApiClient::isConnectFailure)
                .build();
        return registry.retry(RESILIENCE_ID, config);
    }

    @Bean(name = "dfmMemberApiTimeLimiter")
    public TimeLimiter dfmMemberApiTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(properties.readTimeoutSeconds()))
                .build();
        return registry.timeLimiter(RESILIENCE_ID, config);
    }
}
