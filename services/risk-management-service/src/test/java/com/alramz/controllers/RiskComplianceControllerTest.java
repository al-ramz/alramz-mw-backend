package com.alramz.controllers;

import com.alramz.exception.ErrorCode;
import com.alramz.exception.RiskComplianceException;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.Country;
import com.alramz.model.CountryListResponse;
import com.alramz.model.CountryRating;
import com.alramz.model.CountryRatingResponse;
import com.alramz.model.GenericResponse;
import com.alramz.service.RiskComplianceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskComplianceControllerTest {

    private static final String CORRELATION_ID = "a3eac7fd-8693-468a-b194-48a90ce9a3b7";

    @Mock
    private RiskComplianceService service;

    private RiskComplianceController controller;

    @BeforeEach
    void setUp() {
        controller = new RiskComplianceController(service);
        MDCUtil.putCorrelationId(CORRELATION_ID);
    }

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    private static void assertOkEnvelope(ResponseEntity<GenericResponse> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        GenericResponse body = response.getBody();
        assertThat(body.getResponseCode()).isEqualTo("200");
        assertThat(body.getResponseMessage()).isEqualTo("OK");
        assertThat(body.getErrorCode()).isNull();
        assertThat(body.getErrorMsg()).isNull();
    }

    @Test
    void getCountries_shouldWrapTheListInAnOkEnvelope() {
        List<Country> countries = List.of(new Country().name("Afghanistan").iso2("AF").iso3("AFG"));
        when(service.getCountries()).thenReturn(countries);

        ResponseEntity<GenericResponse> response = controller.getCountries();

        assertOkEnvelope(response);
        assertThat(response.getBody().getCorrelationId()).isEqualTo(UUID.fromString(CORRELATION_ID));
        assertThat(response.getBody().getResponse())
                .isEqualTo(new CountryListResponse().countryList(countries));
    }

    @Test
    void getCountries_shouldReturnAnEmptyArrayNotNull() {
        when(service.getCountries()).thenReturn(List.of());

        ResponseEntity<GenericResponse> response = controller.getCountries();

        assertOkEnvelope(response);
        assertThat(((CountryListResponse) response.getBody().getResponse()).getCountryList())
                .isNotNull()
                .isEmpty();
    }

    @Test
    void getCountries_shouldUseAFreshUuidWhenTheHeaderIsNotAUuid() {
        MDCUtil.putCorrelationId("not-a-uuid");
        when(service.getCountries()).thenReturn(List.of());

        assertThat(controller.getCountries().getBody().getCorrelationId()).isNotNull();
    }

    @Test
    void getCountryRatings_shouldWrapTheListInAnOkEnvelope() {
        List<CountryRating> ratings = List.of(new CountryRating().name("Afghanistan").iso2("AF").iso3("AFG")
                .rating(20).riskLevel(CountryRating.RiskLevelEnum.H));
        when(service.getCountryRatings()).thenReturn(ratings);

        ResponseEntity<GenericResponse> response = controller.getCountryRatings();

        assertOkEnvelope(response);
        assertThat(response.getBody().getResponse())
                .isEqualTo(new CountryRatingResponse().countryRating(ratings));
    }

    @Test
    void getCountryRatings_shouldReturnAnEmptyArrayNotNull() {
        when(service.getCountryRatings()).thenReturn(List.of());

        ResponseEntity<GenericResponse> response = controller.getCountryRatings();

        assertThat(((CountryRatingResponse) response.getBody().getResponse()).getCountryRating())
                .isNotNull()
                .isEmpty();
    }

    @Test
    void shouldLetTypedFailuresPropagateToTheExceptionHandler() {
        RiskComplianceException failure = new RiskComplianceException(ErrorCode.CRT003, null);
        when(service.getCountryRatings()).thenThrow(failure);

        assertThatThrownBy(controller::getCountryRatings).isSameAs(failure);
    }
}
