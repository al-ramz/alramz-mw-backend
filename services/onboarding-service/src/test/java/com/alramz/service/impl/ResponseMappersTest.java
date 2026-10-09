package com.alramz.service.impl;

import com.alramz.model.ETradeResponse;
import com.alramz.model.ValidationRequest;
import com.alramz.model.ValidationRequest.ValidationTypeEnum;
import com.alramz.model.ValidationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseMappersTest {

    private JsonNode mockResData(String existsValue) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"Exists\":" + existsValue + "}");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void emailExistsResponseMapper_shouldMapExistsTrue() {
        EmailExistsResponseMapper mapper = new EmailExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1");

        ETradeResponse externalResponse = new ETradeResponse("0", null, mockResData("1"), null);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getExists()).isTrue();
        assertThat(response.getValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Email already exists");
    }

    @Test
    void emailExistsResponseMapper_shouldMapExistsFalse() {
        EmailExistsResponseMapper mapper = new EmailExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1");

        ETradeResponse externalResponse = new ETradeResponse("0", null, mockResData("0"), null);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getExists()).isFalse();
        assertThat(response.getValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Email does not exist");
    }

    @Test
    void emailExistsResponseMapper_shouldHandleErrorResponse() {
        EmailExistsResponseMapper mapper = new EmailExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1");

        JsonNode errorResData = mockResData("{\"message\":\"some error\"}");
        ETradeResponse externalResponse = new ETradeResponse("1", null, errorResData, null);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getValid()).isFalse();
    }

    @Test
    void passportExistsResponseMapper_shouldMapExistsTrue() {
        PassportExistsResponseMapper mapper = new PassportExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.PASSPORT_EXISTS)
                .referenceNo("1");

        ETradeResponse externalResponse = new ETradeResponse("0", null, mockResData("1"), null);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getExists()).isTrue();
        assertThat(response.getValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Passport already exists");
    }

    @Test
    void ninExistsResponseMapper_shouldMapInvertedLogic() {
        NinExistsResponseMapper mapper = new NinExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.NIN_EXISTS)
                .referenceNo("1");

        ETradeResponse externalResponse = new ETradeResponse("0", null, mockResData("0"), null);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getExists()).isTrue();
        assertThat(response.getValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("NIN already exists");
    }

    @Test
    void usernameExistsResponseMapper_shouldMapExists() {
        UsernameExistsResponseMapper mapper = new UsernameExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.USERNAME_EXISTS)
                .referenceNo("1");

        ETradeResponse externalResponse = new ETradeResponse("0", null, mockResData("1"), null);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getExists()).isTrue();
        assertThat(response.getValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Username already exists");
    }

    @Test
    void eidExistsResponseMapper_shouldMapExists() {
        EIDExistsResponseMapper mapper = new EIDExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.EID_EXISTS)
                .referenceNo("1");

        ETradeResponse externalResponse = new ETradeResponse("0", null, mockResData("1"), null);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getExists()).isTrue();
        assertThat(response.getValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("EID already exists");
    }

    @Test
    void tpUuidExistsResponseMapper_shouldUseExistsFieldFirst() {
        TPUUIDExistsResponseMapper mapper = new TPUUIDExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.TP_UUID_EXISTS)
                .referenceNo("1");

        ETradeResponse externalResponse = new ETradeResponse("0", null, null, true);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getExists()).isTrue();
        assertThat(response.getValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Third-party UUID already exists");
    }

    @Test
    void tpUuidExistsResponseMapper_shouldFallbackToResData() {
        TPUUIDExistsResponseMapper mapper = new TPUUIDExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.TP_UUID_EXISTS)
                .referenceNo("1");

        ETradeResponse externalResponse = new ETradeResponse("0", null, mockResData("1"), null);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getExists()).isTrue();
        assertThat(response.getValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Third-party UUID already exists");
    }

    @Test
    void tpUuidExistsResponseMapper_shouldReturnFalseWhenNoData() {
        TPUUIDExistsResponseMapper mapper = new TPUUIDExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.TP_UUID_EXISTS)
                .referenceNo("1");

        ETradeResponse externalResponse = new ETradeResponse("0", null, null, null);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getExists()).isFalse();
        assertThat(response.getValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Third-party UUID does not exist");
    }

    @Test
    void mobileExistsResponseMapper_shouldMapExists() {
        MobileExistsResponseMapper mapper = new MobileExistsResponseMapper();
        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationTypeEnum.MOBILE_EXISTS)
                .referenceNo("1");

        ETradeResponse externalResponse = new ETradeResponse("0", null, mockResData("1"), null);

        ValidationResponse response = mapper.map(externalResponse, request);

        assertThat(response).isNotNull();
        assertThat(response.getExists()).isTrue();
        assertThat(response.getValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Mobile number already exists");
    }
}
