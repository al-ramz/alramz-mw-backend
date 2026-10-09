package com.alramz.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties( IbanServiceProperties.class)
@AllArgsConstructor
public class ExternalIbanServiceConfig {

    private final IbanServiceProperties ibanServiceProperties;

    private static final String CIRCUIT_BREAKER_ID = "iban-circuit-breaker";

    @Bean(name = "ibanValidationService")
    public WebClient ibanService(WebClient.Builder builder, ExchangeFilterFunction alramzWebClientLoggingFilterFunction) {
        return builder
                .baseUrl(ibanServiceProperties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> { configurer.defaultCodecs()
                        .jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper()));
                    configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024);
                })
                .filter(alramzWebClientLoggingFilterFunction)
                .build();
    }

    @Bean(name = "ibanCircuitBreaker")
    public CircuitBreaker ibanServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(CIRCUIT_BREAKER_ID);
    }

    @Bean(name = "ibanRetry")
    public Retry ibanServiceRetry(RetryRegistry registry) {
        return registry.retry(CIRCUIT_BREAKER_ID);
    }

    @Bean(name = "ibanTimeLimiter")
    public TimeLimiter ibanServiceTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(Integer.parseInt(ibanServiceProperties.requestTimeout())))
                .build();
        return registry.timeLimiter(CIRCUIT_BREAKER_ID, config);
    }

    ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build()
                .registerModule(new JavaTimeModule());
    }

}