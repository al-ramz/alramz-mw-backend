package com.alramz.service.impl;

import com.alramz.client.ETradeClient;
import com.alramz.config.ETradeTokenProvider;
import com.alramz.exception.ExternalSystemException;
import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.ETradeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class DuplicateCheckHelper {

    private final ETradeTokenProvider etradeTokenProvider;
    private final ETradeClient etradeClient;
    private final ObjectProvider<NinTradingNumberValidationService> ninTradingNumberValidationServiceProvider;

    public DuplicateCheckHelper(ETradeTokenProvider etradeTokenProvider,
                                ETradeClient etradeClient,
                                ObjectProvider<NinTradingNumberValidationService> ninTradingNumberValidationServiceProvider) {
        this.etradeTokenProvider = etradeTokenProvider;
        this.etradeClient = etradeClient;
        this.ninTradingNumberValidationServiceProvider = ninTradingNumberValidationServiceProvider;
    }

    @Loggable
    public boolean checkNinExists(String nin) {
        NinTradingNumberValidationService ninService = ninTradingNumberValidationServiceProvider.getIfAvailable();
        if (ninService == null) {
            throw new ExternalSystemException("NIN duplicate check service is unavailable");
        }
        try {
            return ninService.checkIfNinOrTradingNumberExists("DFM", nin, null);
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            log.error("NIN duplicate check failed for nin={}", nin, e);
            throw new ExternalSystemException("NIN duplicate check failed: " + e.getMessage());
        }
    }

    @Loggable
    public boolean checkEidExists(String eid) {
        return callETradeWithRetry(
            "/IntegrationAPI/IntegrationWServices/IfEidExists",
            "POST",
            Map.of(
                "Reference_No", "1",
                "eid_number", eid
            )
        );
    }

    @Loggable
    public boolean checkEmailExists(String email) {
        return callETradeWithRetry(
            "/IntegrationAPI/IntegrationWServices/IfEmailExists",
            "POST",
            Map.of(
                "Reference_No", "1",
                "emailAddress", email
            )
        );
    }

    @Loggable
    public boolean checkPassportExists(String passport) {
        return callETradeWithRetry(
            "/IntegrationAPI/IntegrationWServices/IfPassportExists",
            "POST",
            Map.of(
                "Reference_No", "1",
                "pp_number", passport
            )
        );
    }

    private boolean callETradeWithRetry(String endpoint, String method, Object body) {
        java.util.Optional<String> tokenOpt = etradeTokenProvider.getToken();
        if (tokenOpt.isEmpty()) {
            throw new ExternalSystemException("Failed to obtain eTrade access token for duplicate check");
        }
        String accessToken = tokenOpt.get();

        try {
            ETradeResponse response = etradeClient.callETrade(
                    accessToken,
                    endpoint,
                    method,
                    body,
                    ETradeResponse.class
            );
            return evaluateResponse(response);
        } catch (ApiCallFailedException e) {
            if (e.getResponseCode() == 401) {
                log.warn("Received 401 from eTrade during duplicate check, invalidating token and retrying once");
                etradeTokenProvider.invalidate();
                String freshToken = etradeTokenProvider.getToken()
                        .orElseThrow(() -> new ExternalSystemException("Failed to obtain fresh eTrade access token after 401"));
                ETradeResponse response = etradeClient.callETrade(
                        freshToken,
                        endpoint,
                        method,
                        body,
                        ETradeResponse.class
                );
                return evaluateResponse(response);
            }
            throw new ExternalSystemException("eTrade duplicate check service unavailable");
        }
    }

    private boolean evaluateResponse(ETradeResponse response) {
        if (response == null || response.errorCode() == null) {
            return false;
        }
        if (!"0".equals(response.errorCode())) {
            String errorMessage = response.resData() != null
                    ? response.resData().path("message").asText("External API error")
                    : "External API error";
            throw new ExternalSystemException("eTrade duplicate check failed: " + errorMessage);
        }
        return Boolean.TRUE.equals(response.exists());
    }
}
