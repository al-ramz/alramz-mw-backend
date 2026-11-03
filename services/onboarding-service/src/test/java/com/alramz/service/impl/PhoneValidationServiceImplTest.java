package com.alramz.service.impl;

import com.alramz.config.JacksonConfig;
import com.alramz.config.PhoneServiceProperties;
import com.alramz.exception.ExternalSystemException;
import com.alramz.exception.IbanValidationException;
import com.alramz.exception.TechnicalException;
import com.alramz.model.GenericResponse;
import com.alramz.model.PhoneRequest;
import com.alramz.model.PhoneValidationResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneValidationServiceImplTest {

    private static final PhoneServiceProperties PROPS = new PhoneServiceProperties("https://phone.test", "phone-key", "5s");
    private static final String PHONE = "+971501234567";

    private final AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();

    private PhoneValidationServiceImpl service(PhoneServiceProperties props, Mono<ClientResponse> answer) {
        ExchangeFunction exchange = request -> {
            sentRequest.set(request);
            return answer;
        };
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        return new PhoneValidationServiceImpl(
                WebClient.builder().exchangeFunction(exchange).build(),
                CircuitBreaker.ofDefaults("phone"),
                Retry.of("phone", RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiter.ofDefaults("phone"),
                props,
                environment,
                new JacksonConfig().objectMapper());
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private static PhoneRequest request(String phone) {
        PhoneRequest request = new PhoneRequest();
        request.setPhone(phone);
        return request;
    }

    @Test
    void validate_shouldWrapProviderResponseAndSendKeyAndPhone() {
        PhoneValidationServiceImpl service = service(PROPS, json(HttpStatus.OK, "{}"));

        GenericResponse response = service.validate(request(PHONE));

        assertThat(response.getResponseCode()).isEqualTo("200");
        assertThat(response.getResponseMessage()).isEqualTo("OK");
        assertThat(response.getResponse()).isInstanceOf(PhoneValidationResponse.class);
        ClientRequest sent = sentRequest.get();
        assertThat(sent.method()).isEqualTo(HttpMethod.POST);
        assertThat(sent.url().getPath()).isEqualTo("/v2/verify");
        assertThat(sent.url().getQuery()).contains("key=phone-key", "phone=");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void validate_shouldRejectMissingPhone(String phone) {
        PhoneValidationServiceImpl service = service(PROPS, json(HttpStatus.OK, "{}"));

        var input = request(phone);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(IbanValidationException.class)
                .hasMessage("Missing phone number")
                .extracting("code").isEqualTo("1069");
        assertThat(sentRequest.get()).isNull();
    }

    @Test
    void validate_shouldFailWhenApiKeyIsNotConfigured() {
        PhoneValidationServiceImpl service = service(new PhoneServiceProperties("https://phone.test", null, "5s"), json(HttpStatus.OK, "{}"));

        var input = request(PHONE);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("Failed to retrieve phone base URL");
    }

    @Test
    void validate_shouldFailWhenBaseUrlIsNotConfigured() {
        PhoneValidationServiceImpl service = service(new PhoneServiceProperties("", "phone-key", "5s"), json(HttpStatus.OK, "{}"));

        var input = request(PHONE);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("Failed to retrieve phone base URL");
    }

    @Test
    void validate_shouldMapProviderHttpErrorToExternalSystemException() {
        PhoneValidationServiceImpl service = service(PROPS, json(HttpStatus.BAD_GATEWAY, "{\"message\": \"down\"}"));

        var input = request(PHONE);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("Phone validation service unavailable");
    }

    @Test
    void validate_shouldMapEmptyProviderBodyToExternalSystemException() {
        PhoneValidationServiceImpl service = service(PROPS, Mono.just(ClientResponse.create(HttpStatus.OK).build()));

        var input = request(PHONE);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("Phone validation service unavailable");
    }

    @Test
    void validate_shouldMapUnexpectedFailureToTechnicalException() {
        PhoneValidationServiceImpl service = service(PROPS, Mono.error(new IllegalStateException("connection reset")));

        var input = request(PHONE);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(TechnicalException.class)
                .hasMessage("Internal Server Error");
    }
}
