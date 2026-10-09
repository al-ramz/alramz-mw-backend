package com.alramz.service.impl;

import com.alramz.client.AbstractRestClient;
import com.alramz.config.IbanServiceProperties;
import com.alramz.exception.ApplicationException;
import com.alramz.exception.TechnicalException;
import com.alramz.exception.ExternalSystemException;
import com.alramz.exception.IbanValidationException;
import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.BankData;
import com.alramz.model.GenericResponse;
import com.alramz.model.IBANRequest;
import com.alramz.model.IBANValidationResponse;
import com.alramz.model.SepaData;
import com.alramz.model.ValidationResult;
import com.alramz.model.Validations;
import com.alramz.service.IBANValidationService;
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

import java.util.List;

@Service
public class IBANValidationServiceImpl extends AbstractRestClient implements IBANValidationService {

    private final IbanServiceProperties ibanServiceProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IBANValidationServiceImpl(
            @Qualifier("ibanValidationService") WebClient webClient,
            @Qualifier("ibanCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("ibanRetry") Retry retry,
            @Qualifier("ibanTimeLimiter") TimeLimiter timeLimiter,
            IbanServiceProperties ibanServiceProperties,
            Environment environment,
            ObjectMapper objectMapper) {
        super(webClient, circuitBreaker, retry, timeLimiter, environment, objectMapper);

        this.ibanServiceProperties = ibanServiceProperties;
    }

    @Override
    @Loggable
    public GenericResponse validate(IBANRequest request) {
        String iban = request.getIBAN();
        if (iban == null || iban.trim().isEmpty()) {
            throw new IbanValidationException("1069", "Missing IBAN");
        }

        if (ibanServiceProperties.apiKey() == null || ibanServiceProperties.apiKey().isEmpty()
                || ibanServiceProperties.baseUrl() == null || ibanServiceProperties.baseUrl().isEmpty()) {
            throw new ExternalSystemException("Failed to retrieve IBAN base URL");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("iban", iban);
        params.add("format", "json");
        params.add("api_key", ibanServiceProperties.apiKey());

        IBANValidationResponse externalResponse;
        try {
            externalResponse = call(
                    HttpMethod.GET,
                    "/iban/",
                    Void.class,
                    null,
                    IBANValidationResponse.class,
                    null,
                    params
            ).block();
        } catch (ApiCallFailedException e) {
            throw new ExternalSystemException("IBAN validation service unavailable");
        } catch (WebClientResponseException e) {
            throw new ExternalSystemException("IBAN validation service unavailable");
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException
            throw new TechnicalException("Internal Server Error");
        }

        if (externalResponse == null) {
            throw new ExternalSystemException("IBAN validation service unavailable");
        }

        IBANValidationResponse result = applyValidationRules(externalResponse);
        return createSuccessResponse(result);
    }

    private IBANValidationResponse applyValidationRules(IBANValidationResponse response) {
        Validations validations = response.getValidations(); // NOPMD LawOfDemeter

        if (validations == null) {
            return createSuccessResponse(response.getBankData(), response.getSepaData(), response.getValidations());
        }

        ValidationResult chars = validations.getChars();
        ValidationResult account = validations.getAccount();
        ValidationResult iban = validations.getIban();
        ValidationResult length = validations.getLength();
        ValidationResult countrySupport = validations.getCountrySupport();
        ValidationResult structure = validations.getStructure();

        String charsCode = chars != null ? chars.getCode() : null;
        String accountCode = account != null ? account.getCode() : null;
        String ibanCode = iban != null ? iban.getCode() : null;
        String lengthCode = length != null ? length.getCode() : null;
        String countrySupportCode = countrySupport != null ? countrySupport.getCode() : null;
        String structureCode = structure != null ? structure.getCode() : null;

        return createSuccessResponse(response.getBankData(), response.getSepaData(), response.getValidations());
    }

    private IBANValidationResponse createSuccessResponse(BankData bankData, SepaData sepaData, Validations validations) {
        IBANValidationResponse success = new IBANValidationResponse();
        success.setBankData(bankData);
        success.setSepaData(sepaData);
        success.setValidations(validations);
        return success;
    }

    private GenericResponse createSuccessResponse(IBANValidationResponse validationResponse) {
        GenericResponse response = new GenericResponse();
        response.setResponse(validationResponse);
        response.setResponseMessage("OK");
        response.setResponseCode("200");
        return response;
    }
}
