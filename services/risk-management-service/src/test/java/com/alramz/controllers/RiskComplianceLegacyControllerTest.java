package com.alramz.controllers;

import com.alramz.exception.ErrorCode;
import com.alramz.exception.RiskComplianceException;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.Country;
import com.alramz.model.CountryListResponse;
import com.alramz.model.CountryRating;
import com.alramz.model.LegacyCountryListEnvelope;
import com.alramz.model.LegacyCountryRating;
import com.alramz.model.LegacyCountryRatingEnvelope;
import com.alramz.model.LegacyRiskComplianceRequest;
import com.alramz.service.RiskComplianceService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskComplianceLegacyControllerTest {

    private static final String HEADER_CORRELATION_ID = "header-corr-001";

    @Mock
    private RiskComplianceService service;

    private RiskComplianceLegacyController controller;

    @BeforeEach
    void setUp() {
        controller = new RiskComplianceLegacyController(service);
        MDCUtil.putCorrelationId(HEADER_CORRELATION_ID);
    }

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    private static LegacyRiskComplianceRequest body(String correlationId) {
        return new LegacyRiskComplianceRequest().correlationID(correlationId);
    }

    @Test
    void getCountriesLegacy_shouldReturnTheLegacySuccessEnvelope() {
        List<Country> countries = List.of(new Country().name("Afghanistan").iso2("AF").iso3("AFG"));
        when(service.getCountries()).thenReturn(countries);

        ResponseEntity<LegacyCountryListEnvelope> response = controller.getCountriesLegacy(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LegacyCountryListEnvelope envelope = response.getBody();
        assertThat(envelope.getCorrelationID()).isEqualTo(HEADER_CORRELATION_ID);
        assertThat(envelope.getResponseCode()).isEqualTo("200");
        assertThat(envelope.getResponseMessage()).isEqualTo("success");
        assertThat(envelope.getResponse()).isEqualTo(new CountryListResponse().countryList(countries));
    }

    @Test
    void getCountriesLegacy_shouldOmitTheResponseWhenThereAreNoRows() {
        when(service.getCountries()).thenReturn(List.of());

        LegacyCountryListEnvelope envelope = controller.getCountriesLegacy(body(null)).getBody();

        assertThat(envelope.getResponseCode()).isEqualTo("200");
        assertThat(envelope.getResponseMessage()).isEqualTo("success");
        assertThat(envelope.getResponse()).isNull();
    }

    @Test
    void getCountriesLegacy_shouldMapAVendorRejectionTo1018() {
        when(service.getCountries()).thenThrow(new RiskComplianceException(ErrorCode.CLS001, null));

        ResponseEntity<LegacyCountryListEnvelope> response = controller.getCountriesLegacy(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResponseCode()).isEqualTo("1018");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Failed to get countrylist");
        assertThat(response.getBody().getResponse()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"CLS002", "CLS003", "CLS004"})
    void getCountriesLegacy_shouldMapEveryOtherFailureTo500(ErrorCode code) {
        when(service.getCountries()).thenThrow(new RiskComplianceException(code, null));

        ResponseEntity<LegacyCountryListEnvelope> response = controller.getCountriesLegacy(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResponseCode()).isEqualTo("500");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getResponse()).isNull();
    }

    @Test
    void getCountriesLegacy_shouldMapAnUntypedFailureTo500() {
        when(service.getCountries()).thenThrow(new IllegalStateException("bug"));

        LegacyCountryListEnvelope envelope = controller.getCountriesLegacy(null).getBody();

        assertThat(envelope.getResponseCode()).isEqualTo("500");
        assertThat(envelope.getResponseMessage()).isEqualTo("Internal Server Error");
    }

    @Test
    void getCountryRatingsLegacy_shouldReturnStringRatings() {
        when(service.getCountryRatings()).thenReturn(List.of(
                new CountryRating().name("Afghanistan").iso2("AF").iso3("AFG").rating(20)
                        .riskLevel(CountryRating.RiskLevelEnum.H),
                new CountryRating().name("Albania").iso2("AL").iso3("ALB").rating(9)
                        .riskLevel(CountryRating.RiskLevelEnum.M),
                new CountryRating().name("Åland Islands").iso2("AX").iso3("ALA").rating(1)
                        .riskLevel(CountryRating.RiskLevelEnum.L)));

        LegacyCountryRatingEnvelope envelope = controller.getCountryRatingsLegacy(null).getBody();

        assertThat(envelope.getResponseCode()).isEqualTo("200");
        assertThat(envelope.getResponseMessage()).isEqualTo("success");
        assertThat(envelope.getResponse().getCountryRating()).containsExactly(
                new LegacyCountryRating().name("Afghanistan").iso2("AF").iso3("AFG").rating("20")
                        .riskLevel(LegacyCountryRating.RiskLevelEnum.H),
                new LegacyCountryRating().name("Albania").iso2("AL").iso3("ALB").rating("9")
                        .riskLevel(LegacyCountryRating.RiskLevelEnum.M),
                new LegacyCountryRating().name("Åland Islands").iso2("AX").iso3("ALA").rating("1")
                        .riskLevel(LegacyCountryRating.RiskLevelEnum.L));
    }

    @Test
    void getCountryRatingsLegacy_shouldOmitTheResponseWhenEveryRowWasDropped() {
        when(service.getCountryRatings()).thenReturn(List.of());

        LegacyCountryRatingEnvelope envelope = controller.getCountryRatingsLegacy(null).getBody();

        assertThat(envelope.getResponseCode()).isEqualTo("200");
        assertThat(envelope.getResponse()).isNull();
    }

    @Test
    void getCountryRatingsLegacy_shouldMapAVendorRejectionTo1018() {
        when(service.getCountryRatings()).thenThrow(new RiskComplianceException(ErrorCode.CRT001, null));

        LegacyCountryRatingEnvelope envelope = controller.getCountryRatingsLegacy(null).getBody();

        assertThat(envelope.getResponseCode()).isEqualTo("1018");
        assertThat(envelope.getResponseMessage()).isEqualTo("Failed to get countryRating");
        assertThat(envelope.getResponse()).isNull();
    }

    @Test
    void getCountryRatingsLegacy_shouldMapOtherFailuresTo500() {
        when(service.getCountryRatings()).thenThrow(new RiskComplianceException(ErrorCode.CRT003, null));

        LegacyCountryRatingEnvelope envelope = controller.getCountryRatingsLegacy(null).getBody();

        assertThat(envelope.getResponseCode()).isEqualTo("500");
        assertThat(envelope.getResponseMessage()).isEqualTo("Internal Server Error");
    }

    @Test
    void shouldEchoTheBodyCorrelationIdAndPutItInMdc() {
        when(service.getCountries()).thenReturn(List.of());

        LegacyCountryListEnvelope envelope = controller.getCountriesLegacy(body("abc-123")).getBody();

        assertThat(envelope.getCorrelationID()).isEqualTo("abc-123");
        assertThat(MDCUtil.getCorrelationId()).isEqualTo("abc-123");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldFallBackToTheHeaderCorrelationIdForABlankBodyValue(String bodyValue) {
        when(service.getCountryRatings()).thenReturn(List.of());

        LegacyCountryRatingEnvelope envelope = controller.getCountryRatingsLegacy(body(bodyValue)).getBody();

        assertThat(envelope.getCorrelationID()).isEqualTo(HEADER_CORRELATION_ID);
        assertThat(MDCUtil.getCorrelationId()).isEqualTo(HEADER_CORRELATION_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc\r\nFAKE LOG LINE", "abc\u2028x", "tab\there"})
    void shouldFallBackForABodyCorrelationIdWithControlOrNonAsciiChars(String bodyValue) {
        when(service.getCountries()).thenReturn(List.of());

        LegacyCountryListEnvelope envelope = controller.getCountriesLegacy(body(bodyValue)).getBody();

        assertThat(envelope.getCorrelationID()).isEqualTo(HEADER_CORRELATION_ID);
        assertThat(MDCUtil.getCorrelationId()).isEqualTo(HEADER_CORRELATION_ID);
    }

    @Test
    void shouldAcceptA128CharBodyCorrelationIdButFallBackFor129() {
        when(service.getCountries()).thenReturn(List.of());

        assertThat(controller.getCountriesLegacy(body("a".repeat(128))).getBody().getCorrelationID())
                .isEqualTo("a".repeat(128));
        assertThat(controller.getCountriesLegacy(body("b".repeat(129))).getBody().getCorrelationID())
                .isEqualTo("a".repeat(128)); // MDC still holds the previously accepted value
    }

    @Test
    void shouldGenerateACorrelationIdWhenNeitherBodyNorMdcHasOne() {
        MDCUtil.clear();
        when(service.getCountries()).thenReturn(List.of());

        assertThat(controller.getCountriesLegacy(null).getBody().getCorrelationID()).isNotBlank();
    }

    private static HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void handleUnreadableBody_shouldReturnTheLegacyCountries500WithHttp200() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json",
                new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Object> response = controller.handleUnreadableBody(ex, request("/api/v1/countries"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOfSatisfying(LegacyCountryListEnvelope.class, envelope -> {
            assertThat(envelope.getCorrelationID()).isEqualTo(HEADER_CORRELATION_ID);
            assertThat(envelope.getResponseCode()).isEqualTo("500");
            assertThat(envelope.getResponseMessage()).isEqualTo("Internal Server Error");
            assertThat(envelope.getResponse()).isNull();
        });
    }

    @Test
    void handleUnreadableBody_shouldReturnTheLegacyRatings500ForAMediaTypeMismatch() {
        ResponseEntity<Object> response = controller.handleUnreadableBody(
                new HttpMediaTypeNotSupportedException("text/plain"), request("/api/v1/countryRatings"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOfSatisfying(LegacyCountryRatingEnvelope.class,
                envelope -> assertThat(envelope.getResponseCode()).isEqualTo("500"));
    }
}
