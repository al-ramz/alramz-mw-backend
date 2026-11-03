package com.alramz.config;

import com.alramz.openbee.OpenBeeException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
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

/**
 * Open Bee HTTP client wiring (ExternalFinouxServiceConfig precedent). The connector is the JDK HttpClient,
 * set explicitly: no reactor-netty/jetty/httpcomponents reactive connector is on this classpath. Read and
 * transfer timeouts are applied per call by {@code OpenBeeClient}; only the connect timeout lives here.
 * The SNAKE_CASE Jackson 2 {@code objectMapper} bean is deliberately not installed as a codec: Open Bee JSON is
 * camelCase and Boot's default codecs handle it.
 */
@Configuration
@EnableConfigurationProperties(DmsProperties.class)
@RequiredArgsConstructor
public class OpenBeeClientConfig {

    public static final String RESILIENCE_ID = "openbee";
    static final int MAX_IN_MEMORY_BYTES = 5 * 1024 * 1024;

    private final DmsProperties properties;

    @Bean(name = "openBeeWebClient")
    public WebClient openBeeWebClient(WebClient.Builder builder, ExchangeFilterFunction alramzWebClientLoggingFilterFunction) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getOpenbee().getConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return builder
                .baseUrl(properties.getOpenbee().getBaseUrl())
                .clientConnector(new JdkClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .filter(alramzWebClientLoggingFilterFunction)
                .build();
    }

    /** Counts only timeouts, connection errors and 5xx: one consumer's 401/403 must not open it for everyone. */
    @Bean(name = "openBeeCircuitBreaker")
    public CircuitBreaker openBeeCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.from(registry.getDefaultConfig())
                .recordException(OpenBeeClientConfig::isTransient)
                .ignoreException(throwable -> !isTransient(throwable))
                .build();
        return registry.circuitBreaker(RESILIENCE_ID, config);
    }

    /** Read calls only: exponential backoff, retried on timeout/connection/5xx, never on 4xx or an open circuit. */
    @Bean(name = "openBeeReadRetry")
    public Retry openBeeReadRetry(RetryRegistry registry) {
        DmsProperties.Retry retry = properties.getOpenbee().getRetry();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(retry.getMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(retry.getInitialBackoff(), 2.0))
                .retryOnException(OpenBeeClientConfig::isTransient)
                .build();
        return registry.retry(RESILIENCE_ID + "-read", config);
    }

    /** Never retry: an upload is not idempotent, a retry could store a duplicate version. */
    @Bean(name = "openBeeWriteRetry")
    public Retry openBeeWriteRetry(RetryRegistry registry) {
        return registry.retry(RESILIENCE_ID + "-write", RetryConfig.custom().maxAttempts(1).build());
    }

    /** JSON calls: read timeout. Uploads and downloads use transfer-read-timeout instead. */
    @Bean(name = "openBeeTimeLimiter")
    public TimeLimiter openBeeTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(properties.getOpenbee().getReadTimeout())
                .cancelRunningFuture(true)
                .build();
        return registry.timeLimiter(RESILIENCE_ID, config);
    }

    static boolean isTransient(Throwable throwable) {
        return throwable instanceof OpenBeeException e && e.isTransient();
    }
}
