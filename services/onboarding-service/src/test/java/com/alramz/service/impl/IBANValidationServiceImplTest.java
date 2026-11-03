package com.alramz.service.impl;

import com.alramz.config.IbanServiceProperties;
import com.alramz.config.JacksonConfig;
import com.alramz.exception.ExternalSystemException;
import com.alramz.exception.IbanValidationException;
import com.alramz.exception.TechnicalException;
import com.alramz.model.GenericResponse;
import com.alramz.model.IBANRequest;
import com.alramz.model.IBANValidationResponse;
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

class IBANValidationServiceImplTest {

    private static final IbanServiceProperties PROPS = new IbanServiceProperties("https://iban.test", "secret-key", "5s");
    private static final String VALID_IBAN = "AE070331234567890123456";

    private final AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();

    private IBANValidationServiceImpl service(IbanServiceProperties props, Mono<ClientResponse> answer) {
        ExchangeFunction exchange = request -> {
            sentRequest.set(request);
            return answer;
        };
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        return new IBANValidationServiceImpl(
                WebClient.builder().exchangeFunction(exchange).build(),
                CircuitBreaker.ofDefaults("iban"),
                Retry.of("iban", RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiter.ofDefaults("iban"),
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

    private static IBANRequest request(String iban) {
        IBANRequest request = new IBANRequest();
        request.setIBAN(iban);
        return request;
    }

    @Test
    void validate_shouldReturnBankDataAndValidationsFromProvider() {
        IBANValidationServiceImpl service = service(PROPS, json(HttpStatus.OK, """
                {"bank_data": {"bic": "BOMLAEAD", "branch": "Dubai"},
                 "validations": {"iban": {"code": "001", "message": "IBAN Check digit is correct"},
                                 "chars": {"code": "006", "message": "IBAN does not contain illegal characters"}}}
                """));

        GenericResponse response = service.validate(request(VALID_IBAN));

        assertThat(response.getResponseCode()).isEqualTo("200");
        assertThat(response.getResponseMessage()).isEqualTo("OK");
        IBANValidationResponse body = (IBANValidationResponse) response.getResponse();
        assertThat(body.getBankData().getBic()).isEqualTo("BOMLAEAD");
        assertThat(body.getValidations().getIban().getCode()).isEqualTo("001");
        assertThat(body.getValidations().getChars().getCode()).isEqualTo("006");

        ClientRequest sent = sentRequest.get();
        assertThat(sent.method()).isEqualTo(HttpMethod.GET);
        assertThat(sent.url().getPath()).isEqualTo("/iban/");
        assertThat(sent.url().getQuery()).contains("iban=" + VALID_IBAN, "format=json", "api_key=secret-key");
    }

    @Test
    void validate_shouldReturnBankDataWhenProviderSendsNoValidations() {
        IBANValidationServiceImpl service = service(PROPS, json(HttpStatus.OK, "{\"bank_data\": {\"bic\": \"BOMLAEAD\"}}"));

        IBANValidationResponse body = (IBANValidationResponse) service.validate(request(VALID_IBAN)).getResponse();

        assertThat(body.getBankData().getBic()).isEqualTo("BOMLAEAD");
        assertThat(body.getValidations()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void validate_shouldRejectMissingIban(String iban) {
        IBANValidationServiceImpl service = service(PROPS, json(HttpStatus.OK, "{}"));

        var input = request(iban);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(IbanValidationException.class)
                .hasMessage("Missing IBAN")
                .extracting("code").isEqualTo("1069");
        assertThat(sentRequest.get()).isNull();
    }

    @Test
    void validate_shouldFailWhenApiKeyIsNotConfigured() {
        IBANValidationServiceImpl service = service(new IbanServiceProperties("https://iban.test", "", "5s"), json(HttpStatus.OK, "{}"));

        var input = request(VALID_IBAN);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("Failed to retrieve IBAN base URL");
    }

    @Test
    void validate_shouldFailWhenBaseUrlIsNotConfigured() {
        IBANValidationServiceImpl service = service(new IbanServiceProperties(null, "secret-key", "5s"), json(HttpStatus.OK, "{}"));

        var input = request(VALID_IBAN);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("Failed to retrieve IBAN base URL");
    }

    @Test
    void validate_shouldMapProviderHttpErrorToExternalSystemException() {
        IBANValidationServiceImpl service = service(PROPS, json(HttpStatus.INTERNAL_SERVER_ERROR, "{\"message\": \"down\"}"));

        var input = request(VALID_IBAN);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("IBAN validation service unavailable");
    }

    @Test
    void validate_shouldMapEmptyProviderBodyToExternalSystemException() {
        IBANValidationServiceImpl service = service(PROPS, Mono.just(ClientResponse.create(HttpStatus.OK).build()));

        var input = request(VALID_IBAN);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("IBAN validation service unavailable");
    }

    @Test
    void validate_shouldMapUnexpectedFailureToTechnicalException() {
        IBANValidationServiceImpl service = service(PROPS, Mono.error(new IllegalStateException("connection reset")));

        var input = request(VALID_IBAN);

        assertThatThrownBy(() -> service.validate(input))
                .isInstanceOf(TechnicalException.class)
                .hasMessage("Internal Server Error");
    }
}
