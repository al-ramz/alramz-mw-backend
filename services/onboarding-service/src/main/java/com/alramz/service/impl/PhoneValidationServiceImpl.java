package com.alramz.service.impl;

import com.alramz.client.AbstractRestClient;
import com.alramz.config.PhoneServiceProperties;
import com.alramz.exception.TechnicalException;
import com.alramz.exception.ExternalSystemException;
import com.alramz.exception.IbanValidationException;
import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.GenericResponse;
import com.alramz.model.PhoneRequest;
import com.alramz.model.PhoneValidationResponse;
import com.alramz.service.PhoneValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class PhoneValidationServiceImpl extends AbstractRestClient implements PhoneValidationService {

    private final PhoneServiceProperties phoneServiceProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PhoneValidationServiceImpl(
            @Qualifier("phoneValidationService") WebClient webClient,
            @Qualifier("phoneCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("phoneRetry") Retry retry,
            @Qualifier("phoneTimeLimiter") TimeLimiter timeLimiter,
            PhoneServiceProperties phoneServiceProperties,
            Environment environment,
            ObjectMapper objectMapper) {
        super(webClient, circuitBreaker, retry, timeLimiter, environment, objectMapper);
        this.phoneServiceProperties = phoneServiceProperties;
    }

    @Override
    @Loggable
    public GenericResponse validate(PhoneRequest request) {
        if (request.getPhone() == null || request.getPhone().trim().isEmpty()) {
            throw new IbanValidationException("1069", "Missing phone number");
        }

        if (phoneServiceProperties.apiKey() == null || phoneServiceProperties.apiKey().isEmpty()
                || phoneServiceProperties.baseUrl() == null || phoneServiceProperties.baseUrl().isEmpty()) {
            throw new ExternalSystemException("Failed to retrieve phone base URL");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("key", phoneServiceProperties.apiKey());
        params.add("phone", request.getPhone());

        PhoneValidationResponse externalResponse;
        try {
            externalResponse = call(
                    HttpMethod.POST,
                    "/v2/verify",
                    Void.class,
                    null,
                    PhoneValidationResponse.class,
                    null,
                    params
            ).block();
        } catch (ApiCallFailedException e) {
            throw new ExternalSystemException("Phone validation service unavailable");
        } catch (WebClientResponseException e) {
            throw new ExternalSystemException("Phone validation service unavailable");
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException
            throw new TechnicalException("Internal Server Error");
        }

        if (externalResponse == null) {
            throw new ExternalSystemException("Phone validation service unavailable");
        }

        GenericResponse response = new GenericResponse();
        response.setResponse(externalResponse);
        response.setResponseMessage("OK");
        response.setResponseCode("200");
        return response;
    }
}
