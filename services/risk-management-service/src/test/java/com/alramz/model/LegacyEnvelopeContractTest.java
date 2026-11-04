package com.alramz.model;

import com.alramz.logging.advice.CorrelationIdResponseBodyAdvice;
import com.alramz.logging.util.MDCUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Wire-shape guarantees of the legacy envelopes that a direct controller call cannot see. */
class LegacyEnvelopeContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    @Test
    void countryListEnvelope_shouldOmitAnUnsetResponseAndUseCorrelationID() {
        LegacyCountryListEnvelope envelope = new LegacyCountryListEnvelope()
                .correlationID("abc-123").responseCode("1018").responseMessage("Failed to get countrylist");

        JsonNode json = mapper.valueToTree(envelope);

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("correlationID", "responseCode", "responseMessage");
        assertThat(json.get("correlationID").asString()).isEqualTo("abc-123");
    }

    @Test
    void countryListEnvelope_shouldSerializeCountriesUnchanged() {
        LegacyCountryListEnvelope envelope = new LegacyCountryListEnvelope()
                .correlationID("abc-123").responseCode("200").responseMessage("success")
                .response(new CountryListResponse().countryList(List.of(
                        new Country().name("Afghanistan").iso2("AF").iso3("AFG"))));

        JsonNode country = mapper.valueToTree(envelope).get("response").get("countryList").get(0);

        assertThat(country.propertyNames()).containsExactlyInAnyOrder("name", "iso2", "iso3");
    }

    @Test
    void countryRatingEnvelope_shouldSerializeRatingAsAJsonString() {
        LegacyCountryRatingEnvelope envelope = new LegacyCountryRatingEnvelope()
                .correlationID("abc-123").responseCode("200").responseMessage("success")
                .response(new LegacyCountryRatingData().countryRating(List.of(new LegacyCountryRating()
                        .name("Afghanistan").iso2("AF").iso3("AFG").rating("20")
                        .riskLevel(LegacyCountryRating.RiskLevelEnum.H))));

        JsonNode json = mapper.valueToTree(envelope);
        JsonNode rating = json.get("response").get("countryRating").get(0);

        assertThat(json.has("errorCode")).isFalse();
        assertThat(json.has("errorMsg")).isFalse();
        assertThat(json.has("correlationId")).isFalse();
        assertThat(rating.get("rating").isString()).isTrue();
        assertThat(rating.get("rating").asString()).isEqualTo("20");
        assertThat(rating.get("riskLevel").asString()).isEqualTo("H");
    }

    @Test
    void countryRatingEnvelope_shouldOmitAnUnsetResponse() {
        JsonNode json = mapper.valueToTree(new LegacyCountryRatingEnvelope()
                .correlationID("abc-123").responseCode("500").responseMessage("Internal Server Error"));

        assertThat(json.has("response")).isFalse();
    }

    @Test
    void envelopes_shouldExposeNoSetterTheStarterAdviceCanFind() {
        for (Class<?> type : List.of(LegacyCountryListEnvelope.class, LegacyCountryRatingEnvelope.class)) {
            assertThatThrownBy(() -> type.getMethod("setCorrelationId", String.class))
                    .isInstanceOf(NoSuchMethodException.class);
            assertThatThrownBy(() -> type.getMethod("setCorrelationId", UUID.class))
                    .isInstanceOf(NoSuchMethodException.class);
        }
    }

    @Test
    void correlationIdAdvice_shouldLeaveTheLegacyCorrelationIdUnchanged() {
        MDCUtil.putCorrelationId(UUID.randomUUID().toString());
        LegacyCountryListEnvelope envelope = new LegacyCountryListEnvelope().correlationID("abc-123");

        Object written = new CorrelationIdResponseBodyAdvice()
                .beforeBodyWrite(envelope, null, null, null, null, null);

        assertThat(((LegacyCountryListEnvelope) written).getCorrelationID()).isEqualTo("abc-123");
    }
}
