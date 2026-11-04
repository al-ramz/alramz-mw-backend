package com.alramz.knowyourcountry;

import com.alramz.client.AbstractRestClient;
import com.alramz.exceptions.ApiCallFailedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.codec.CodecException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Calls the KnowYourCountry vendor and translates every outcome into a {@link KnowYourCountryResult}.
 * The ApiKey header is owned by the WebClient filter (see KnowYourCountryConfig), never set per call.
 */
@Slf4j
@Component
public class KnowYourCountryClient extends AbstractRestClient {

    private static final String COUNTRIES_PATH = "/api/v4/countries_list";
    private static final String RATINGS_PATH = "/api/v4/country_ratings";

    public KnowYourCountryClient(
            @Qualifier("knowYourCountryWebClient") WebClient webClient,
            @Qualifier("knowYourCountryCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("knowYourCountryRetry") Retry retry,
            @Qualifier("knowYourCountryTimeLimiter") TimeLimiter timeLimiter,
            Environment environment,
            ObjectMapper objectMapper) {
        super(webClient, circuitBreaker, retry, timeLimiter, environment, objectMapper);
    }

    public KnowYourCountryResult fetchCountries() {
        return fetch(COUNTRIES_PATH);
    }

    public KnowYourCountryResult fetchCountryRatings() {
        return fetch(RATINGS_PATH);
    }

    private KnowYourCountryResult fetch(String path) {
        KnowYourCountryRecord[] body;
        try {
            body = call(HttpMethod.GET, path, Void.class, null, KnowYourCountryRecord[].class).block();
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException - block() rethrows every failure unchecked; classified below
            return classify(e);
        }
        if (body == null) {
            return new KnowYourCountryResult.Malformed("empty response body");
        }
        List<KnowYourCountryRecord> rows = Arrays.stream(body).filter(Objects::nonNull).toList();
        if (rows.size() < body.length) {
            // Count only: the vendor body is never logged (F-06).
            log.warn("Dropped {} null element(s) from KnowYourCountry {}", body.length - rows.size(), path);
        }
        return new KnowYourCountryResult.Success(rows);
    }

    private static KnowYourCountryResult classify(RuntimeException failure) {
        Throwable cause = Exceptions.unwrap(failure);
        if (cause instanceof ApiCallFailedException rejected) {
            return new KnowYourCountryResult.Rejected(rejected.getResponseCode());
        }
        // A 2xx non-JSON body (e.g. an HTML proxy page) arrives wrapped in a WebClientResponseException.
        Throwable bodyError = cause instanceof WebClientResponseException wrapped && wrapped.getCause() != null
                ? wrapped.getCause() : cause;
        if (bodyError instanceof CodecException || bodyError instanceof UnsupportedMediaTypeException) {
            return new KnowYourCountryResult.Malformed(bodyError.getClass().getSimpleName());
        }
        if (cause instanceof TimeoutException || cause instanceof WebClientRequestException
                || cause instanceof CallNotPermittedException) {
            return new KnowYourCountryResult.Unavailable(cause);
        }
        throw failure;
    }
}
