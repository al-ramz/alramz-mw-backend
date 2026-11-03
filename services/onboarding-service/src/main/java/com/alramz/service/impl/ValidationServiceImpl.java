package com.alramz.service.impl;

import com.alramz.client.ETradeClient;
import com.alramz.config.ETradeTokenProvider;
import com.alramz.config.ValidationDefinition;
import com.alramz.config.ValidationDefinitionRegistry;
import com.alramz.exception.ApplicationException;
import com.alramz.exception.ExternalSystemException;
import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.ETradeResponse;
import com.alramz.model.GenericResponse;
import com.alramz.model.ValidationRequest;
import com.alramz.model.ValidationRequest.ValidationTypeEnum;
import com.alramz.model.ValidationResponse;
import com.alramz.service.ValidationRequestMapper;
import com.alramz.service.ValidationResponseMapper;
import com.alramz.service.ValidationResponseMapperRegistry;
import com.alramz.service.ValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ValidationServiceImpl implements ValidationService {

    private final ETradeTokenProvider etradeTokenProvider;
    private final ValidationDefinitionRegistry definitionRegistry;
    private final ValidationRequestMapper requestMapper;
    private final ValidationResponseMapperRegistry responseMapperRegistry;
    private final ETradeClient etradeClient;

    public ValidationServiceImpl(ETradeTokenProvider etradeTokenProvider,
                                 ValidationDefinitionRegistry definitionRegistry,
                                 ValidationRequestMapper requestMapper,
                                 ValidationResponseMapperRegistry responseMapperRegistry,
                                 ETradeClient etradeClient) {
        this.etradeTokenProvider = etradeTokenProvider;
        this.definitionRegistry = definitionRegistry;
        this.requestMapper = requestMapper;
        this.responseMapperRegistry = responseMapperRegistry;
        this.etradeClient = etradeClient;
    }

    @Override
    @Loggable
    public GenericResponse validate(ValidationRequest request) {
        validateRequest(request);

        java.util.Optional<String> tokenOpt = etradeTokenProvider.getToken();
        if (tokenOpt.isEmpty()) {
            throw new ExternalSystemException("Failed to obtain eTrade access token");
        }
        String accessToken = tokenOpt.get();

        ValidationDefinition definition = definitionRegistry.get(request.getValidationType());

        ETradeResponse externalResponse = callExternalWithRetry(request, accessToken, definition);

        if (!"0".equals(externalResponse.errorCode())) {
            String errorMessage = externalResponse.resData() != null
                    ? externalResponse.resData().path("message").asText("External API error")
                    : "External API error";
            throw new ExternalSystemException("eTrade validation failed: " + errorMessage);
        }

        ValidationResponseMapper mapper = responseMapperRegistry.get(request.getValidationType());
        ValidationResponse validationResponse = mapper.map(externalResponse, request);

        GenericResponse response = new GenericResponse();
        response.setResponseCode("200");
        response.setResponseMessage("OK");
        response.setResponse(validationResponse);
        return response;
    }

    private ETradeResponse callExternalWithRetry(ValidationRequest request, String accessToken, ValidationDefinition definition) {
        try {
            return etradeClient.callETrade(
                    accessToken,
                    definition.endpoint(),
                    definition.method(),
                    requestMapper.map(request, definition),
                    ETradeResponse.class
            );
        } catch (ApiCallFailedException e) {
            if (e.getResponseCode() == HttpStatus.UNAUTHORIZED.value()) {
                log.warn("Received 401 from eTrade, invalidating token and retrying once");
                etradeTokenProvider.invalidate();
                String freshToken = etradeTokenProvider.getToken()
                        .orElseThrow(() -> new ExternalSystemException("Failed to obtain fresh eTrade access token after 401"));
                return etradeClient.callETrade(
                        freshToken,
                        definition.endpoint(),
                        definition.method(),
                        requestMapper.map(request, definition),
                        ETradeResponse.class
                );
            }
            throw new ExternalSystemException("eTrade validation service unavailable", e);
        }
    }

    private void validateRequest(ValidationRequest request) {
        ValidationTypeEnum type = request.getValidationType(); // NOPMD LawOfDemeter
        switch (type) {
            case EMAIL_EXISTS -> requireField(request.getEmail(), "email", type);
            case PASSPORT_EXISTS -> requireField(request.getPassportNumber(), "passportNumber", type);
            case NIN_EXISTS -> requireField(request.getNin(), "nin", type);
            case USERNAME_EXISTS -> requireField(request.getUsername(), "username", type);
            case EID_EXISTS -> requireField(request.getEidNumber(), "eidNumber", type);
            case TP_UUID_EXISTS -> {
                requireField(request.getUuid(), "uuid", type);
                requireField(request.getThirdParty(), "thirdParty", type);
            }
            case MOBILE_EXISTS -> requireField(request.getMobileNumber(), "mobileNumber", type);
            case null, default -> throw new ApplicationException("validationType", "Unsupported validation type: " + type);
        }
    }

    private static void requireField(String value, String field, ValidationTypeEnum type) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApplicationException(field, field + " is required for " + type.name() + " validation");
        }
    }
}
