package com.alramz.service.impl;

import com.alramz.client.ETradeClient;
import com.alramz.config.ETradeProperties;
import com.alramz.config.ETradeTokenProvider;
import com.alramz.config.ValidationDefinition;
import com.alramz.config.ValidationDefinitionRegistry;
import com.alramz.exception.ApplicationException;
import com.alramz.exception.ExternalSystemException;
import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.model.ETradeResponse;
import com.alramz.model.GenericResponse;
import com.alramz.model.ValidationRequest;
import com.alramz.model.ValidationRequest.ValidationTypeEnum;
import com.alramz.model.ValidationResponse;
import com.alramz.service.ValidationRequestMapper;
import com.alramz.service.ValidationResponseMapper;
import com.alramz.service.ValidationResponseMapperRegistry;
import com.alramz.service.ValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.anyMap;

@ExtendWith(MockitoExtension.class)
class ValidationServiceImplTest {

    @Mock
    private ETradeTokenProvider etradeTokenProvider;

    @Mock
    private ValidationRequestMapper requestMapper;

    @Mock
    private ValidationResponseMapperRegistry responseMapperRegistry;

    @Mock
    private ETradeClient etradeClient;

    @Mock
    private ValidationResponseMapper responseMapper;

    @Mock
    private JsonNode resData;

    private ValidationServiceImpl validationService;
    private ValidationDefinitionRegistry definitionRegistry;
    private ETradeProperties etradeProperties;

    private JsonNode mockResData(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        etradeProperties = new ETradeProperties(
                "https://etradeqa.alramz.ae", "1", "R@mZS4qR5t", null, "30", 3000, "/IntegrationAPI/IntegrationWServices/GetClientToken",
                Map.of("EMAIL_EXISTS", new ETradeProperties.EndpointConfig("/path", "POST", Map.of()))
        );
        definitionRegistry = new ValidationDefinitionRegistry(etradeProperties);

        validationService = new ValidationServiceImpl(
                etradeTokenProvider, definitionRegistry, requestMapper, responseMapperRegistry, etradeClient, etradeProperties
        );
    }

    @Test
    void validate_shouldThrowApplicationExceptionWhenEmailMissing() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1");

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", "email");
    }

    @Test
    void validate_shouldThrowApplicationExceptionWhenPassportMissing() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.PASSPORT_EXISTS)
                .referenceNo("1");

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", "passportNumber");
    }

    @Test
    void validate_shouldThrowApplicationExceptionWhenNinMissing() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.NIN_EXISTS)
                .referenceNo("1");

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", "nin");
    }

    @Test
    void validate_shouldThrowApplicationExceptionWhenUsernameMissing() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.USERNAME_EXISTS)
                .referenceNo("1");

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", "username");
    }

    @Test
    void validate_shouldThrowApplicationExceptionWhenEidMissing() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EID_EXISTS)
                .referenceNo("1");

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", "eidNumber");
    }

    @Test
    void validate_shouldThrowApplicationExceptionWhenUuidMissing() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.TP_UUID_EXISTS)
                .referenceNo("1");

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", "uuid");
    }

    @Test
    void validate_shouldThrowApplicationExceptionWhenThirdPartyMissing() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.TP_UUID_EXISTS)
                .referenceNo("1")
                .uuid("some-uuid");

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", "thirdParty");
    }

    @Test
    void validate_shouldThrowApplicationExceptionWhenMobileMissing() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.MOBILE_EXISTS)
                .referenceNo("1");

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", "mobileNumber");
    }

    @Test
    void validate_shouldThrowApplicationExceptionForUnsupportedType() {
        ValidationRequest request = new ValidationRequest()
                .validationType(null)
                .referenceNo("1");

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", "validationType");
    }

    @Test
    void validate_shouldThrowExternalSystemExceptionWhenTokenMissing() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1")
                .email("test@example.com");

        when(etradeTokenProvider.getToken()).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("Failed to obtain eTrade access token");
    }

    @Test
    void validate_shouldThrowExternalSystemExceptionWhenApiReturnsError() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1")
                .email("test@example.com");

        when(etradeTokenProvider.getToken()).thenReturn(java.util.Optional.of("token"));
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), anyMap(), any(Class.class)))
                .thenReturn(new ETradeResponse("1", null, mockResData("{\"message\":\"error\"}"), null));

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessageContaining("eTrade validation failed");
    }

    @Test
    void validate_shouldReturnSuccessResponse() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1")
                .email("test@example.com");

        when(etradeTokenProvider.getToken()).thenReturn(java.util.Optional.of("token"));
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), anyMap(), any(Class.class)))
                .thenReturn(new ETradeResponse("0", null, resData, null));
        when(responseMapperRegistry.get(ValidationTypeEnum.EMAIL_EXISTS)).thenReturn(responseMapper);
        when(responseMapper.map(any(ETradeResponse.class), any(ValidationRequest.class)))
                .thenReturn(new ValidationResponse(ValidationTypeEnum.EMAIL_EXISTS.getValue(), true, true, "exists", "1"));

        GenericResponse response = validationService.validate(request);

        assertThat(response).isNotNull();
        assertThat(response.getResponseCode()).isEqualTo("200");
        assertThat(response.getResponseMessage()).isEqualTo("OK");
    }

    @Test
    void validate_shouldRetryOn401AndSucceed() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1")
                .email("test@example.com");

        when(etradeTokenProvider.getToken()).thenReturn(java.util.Optional.of("token"));

        ApiCallFailedException apiException = new ApiCallFailedException("/path", "POST", 401, "Unauthorized");
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), anyMap(), any(Class.class)))
                .thenThrow(apiException)
                .thenReturn(new ETradeResponse("0", null, resData, null));
        when(responseMapperRegistry.get(ValidationTypeEnum.EMAIL_EXISTS)).thenReturn(responseMapper);
        when(responseMapper.map(any(ETradeResponse.class), any(ValidationRequest.class)))
                .thenReturn(new ValidationResponse(ValidationTypeEnum.EMAIL_EXISTS.getValue(), true, true, "exists", "1"));

        GenericResponse response = validationService.validate(request);

        assertThat(response).isNotNull();
        assertThat(response.getResponseCode()).isEqualTo("200");
        verify(etradeTokenProvider, times(2)).getToken();
    }

    @Test
    void validate_shouldThrowOn401RetryFailure() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1")
                .email("test@example.com");

        when(etradeTokenProvider.getToken()).thenReturn(java.util.Optional.of("token"), java.util.Optional.empty());

        ApiCallFailedException apiException = new ApiCallFailedException("/path", "POST", 401, "Unauthorized");
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), anyMap(), any(Class.class)))
                .thenThrow(apiException);

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("Failed to obtain fresh eTrade access token after 401");
    }

    @Test
    void validate_shouldThrowOnNon401Error() {
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1")
                .email("test@example.com");

        when(etradeTokenProvider.getToken()).thenReturn(java.util.Optional.of("token"));

        ApiCallFailedException apiException = new ApiCallFailedException("/path", "POST", 500, "Server Error");
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), anyMap(), any(Class.class)))
                .thenThrow(apiException);

        assertThatThrownBy(() -> validationService.validate(request))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessage("eTrade validation service unavailable");
    }
}
