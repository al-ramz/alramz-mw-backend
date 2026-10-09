package com.alramz.service.impl;

import com.alramz.client.ETradeClient;
import com.alramz.config.ETradeTokenProvider;
import com.alramz.exception.ExternalSystemException;
import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.model.ETradeResponse;
import com.alramz.service.impl.NinTradingNumberValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DuplicateCheckHelperTest {

    @Mock
    private ETradeTokenProvider etradeTokenProvider;

    @Mock
    private ETradeClient etradeClient;

    private JsonNode mockResData(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DuplicateCheckHelper helperWithNinService(NinTradingNumberValidationService ninService) {
        ObjectProvider<NinTradingNumberValidationService> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ninService);
        return new DuplicateCheckHelper(etradeTokenProvider, etradeClient, provider);
    }

    private DuplicateCheckHelper helperWithoutNinService() {
        ObjectProvider<NinTradingNumberValidationService> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        return new DuplicateCheckHelper(etradeTokenProvider, etradeClient, provider);
    }

    private DuplicateCheckHelper helperForETrade() {
        ObjectProvider<NinTradingNumberValidationService> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        return new DuplicateCheckHelper(etradeTokenProvider, etradeClient, provider);
    }

    @Test
    void checkNinExists_shouldReturnTrueWhenNinExists() {
        NinTradingNumberValidationService ninService = org.mockito.Mockito.mock(NinTradingNumberValidationService.class);
        when(ninService.checkIfNinOrTradingNumberExists("DFM", "NIN123", null)).thenReturn(true);

        DuplicateCheckHelper helper = helperWithNinService(ninService);

        boolean result = helper.checkNinExists("NIN123");

        assertThat(result).isTrue();
        verify(ninService).checkIfNinOrTradingNumberExists("DFM", "NIN123", null);
    }

    @Test
    void checkNinExists_shouldThrowWhenNinServiceUnavailable() {
        DuplicateCheckHelper helper = helperWithoutNinService();

        assertThatThrownBy(() -> helper.checkNinExists("NIN123"))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessageContaining("NIN duplicate check service is unavailable");
    }

    @Test
    void checkEidExists_shouldReturnTrueWhenEidExists() {
        DuplicateCheckHelper helper = helperForETrade();
        when(etradeTokenProvider.getToken()).thenReturn(Optional.of("token"));
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new ETradeResponse("0", null, mockResData("{\"Exists\":true}"), true));

        boolean result = helper.checkEidExists("EID123");

        assertThat(result).isTrue();
    }

    @Test
    void checkEmailExists_shouldReturnTrueWhenEmailExists() {
        DuplicateCheckHelper helper = helperForETrade();
        when(etradeTokenProvider.getToken()).thenReturn(Optional.of("token"));
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new ETradeResponse("0", null, mockResData("{\"Exists\":true}"), true));

        boolean result = helper.checkEmailExists("test@example.com");

        assertThat(result).isTrue();
    }

    @Test
    void checkPassportExists_shouldReturnTrueWhenPassportExists() {
        DuplicateCheckHelper helper = helperForETrade();
        when(etradeTokenProvider.getToken()).thenReturn(Optional.of("token"));
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new ETradeResponse("0", null, mockResData("{\"Exists\":true}"), true));

        boolean result = helper.checkPassportExists("P1234567");

        assertThat(result).isTrue();
    }

    @Test
    void checkEidExists_shouldThrowWhenTokenMissing() {
        DuplicateCheckHelper helper = helperForETrade();
        when(etradeTokenProvider.getToken()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> helper.checkEidExists("EID123"))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessageContaining("Failed to obtain eTrade access token");
    }

    @Test
    void checkEidExists_shouldThrowWhenETradeReturnsError() {
        DuplicateCheckHelper helper = helperForETrade();
        when(etradeTokenProvider.getToken()).thenReturn(Optional.of("token"));
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new ETradeResponse("1", null, mockResData("{\"message\":\"error\"}"), null));

        assertThatThrownBy(() -> helper.checkEidExists("EID123"))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessageContaining("eTrade duplicate check failed");
    }

    @Test
    void checkEidExists_shouldRetryOn401AndSucceed() {
        DuplicateCheckHelper helper = helperForETrade();
        when(etradeTokenProvider.getToken()).thenReturn(Optional.of("token"));

        ApiCallFailedException apiException = new ApiCallFailedException("/path", "POST", 401, "Unauthorized");
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(apiException)
                .thenReturn(new ETradeResponse("0", null, mockResData("{\"Exists\":true}"), true));

        boolean result = helper.checkEidExists("EID123");

        assertThat(result).isTrue();
        verify(etradeTokenProvider, times(2)).getToken();
    }

    @Test
    void checkEidExists_shouldThrowExternalSystemExceptionOn401RetryFailure() {
        DuplicateCheckHelper helper = helperForETrade();
        when(etradeTokenProvider.getToken()).thenReturn(Optional.of("token"), Optional.empty());

        ApiCallFailedException apiException = new ApiCallFailedException("/path", "POST", 401, "Unauthorized");
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(apiException);

        assertThatThrownBy(() -> helper.checkEidExists("EID123"))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessageContaining("Failed to obtain fresh eTrade access token after 401");
    }

    @Test
    void checkEidExists_shouldThrowExternalSystemExceptionOnNon401Error() {
        DuplicateCheckHelper helper = helperForETrade();
        when(etradeTokenProvider.getToken()).thenReturn(Optional.of("token"));

        ApiCallFailedException apiException = new ApiCallFailedException("/path", "POST", 500, "Server Error");
        when(etradeClient.callETrade(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(apiException);

        assertThatThrownBy(() -> helper.checkEidExists("EID123"))
                .isInstanceOf(ExternalSystemException.class)
                .hasMessageContaining("eTrade duplicate check service unavailable");
    }
}
