package com.alramz.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alramz.exception.ErrorCode;
import com.alramz.exception.RiskComplianceException;
import com.alramz.knowyourcountry.KnowYourCountryClient;
import com.alramz.knowyourcountry.KnowYourCountryRecord;
import com.alramz.knowyourcountry.KnowYourCountryResult;
import com.alramz.model.Country;
import com.alramz.model.CountryRating;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskComplianceServiceTest {

    private static final String UNMAPPED_COUNTER = "risk.compliance.country.rating.unmapped";

    @Mock
    private KnowYourCountryClient client;

    private SimpleMeterRegistry meterRegistry;
    private RiskComplianceService service;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new RiskComplianceService(client, meterRegistry);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(RiskComplianceService.class)).addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        ((Logger) LoggerFactory.getLogger(RiskComplianceService.class)).detachAppender(logs);
    }

    private static KnowYourCountryResult rows(KnowYourCountryRecord... records) {
        return new KnowYourCountryResult.Success(Arrays.asList(records));
    }

    private double unmappedCount() {
        return meterRegistry.get(UNMAPPED_COUNTER).counter().count();
    }

    private List<ILoggingEvent> warnings() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    @Test
    void getCountries_shouldMapEveryRowInVendorOrder() {
        when(client.fetchCountries()).thenReturn(rows(
                new KnowYourCountryRecord("Zimbabwe", "ZW", "ZWE", null),
                new KnowYourCountryRecord("Afghanistan", "AF", "AFG", "Higher Risk")));

        List<Country> countries = service.getCountries();

        assertThat(countries).containsExactly(
                new Country().name("Zimbabwe").iso2("ZW").iso3("ZWE"),
                new Country().name("Afghanistan").iso2("AF").iso3("AFG"));
    }

    @Test
    void getCountries_shouldPassNonIsoValuesThrough() {
        when(client.fetchCountries()).thenReturn(rows(new KnowYourCountryRecord(null, "Kosovo", "XK-1", null)));

        assertThat(service.getCountries()).containsExactly(new Country().name(null).iso2("Kosovo").iso3("XK-1"));
    }

    @Test
    void getCountries_shouldReturnAnEmptyListForAnEmptyVendorArray() {
        when(client.fetchCountries()).thenReturn(rows());

        assertThat(service.getCountries()).isEmpty();
    }

    @Test
    void getCountryRatings_shouldMapEachBand() {
        when(client.fetchCountryRatings()).thenReturn(rows(
                new KnowYourCountryRecord("Afghanistan", "AF", "AFG", "Higher Risk"),
                new KnowYourCountryRecord("Albania", "AL", "ALB", "Medium-Low Risk"),
                new KnowYourCountryRecord("Åland Islands", "AX", "ALA", "Lower Risk")));

        List<CountryRating> ratings = service.getCountryRatings();

        assertThat(ratings).containsExactly(
                new CountryRating().name("Afghanistan").iso2("AF").iso3("AFG").rating(20)
                        .riskLevel(CountryRating.RiskLevelEnum.H),
                new CountryRating().name("Albania").iso2("AL").iso3("ALB").rating(9)
                        .riskLevel(CountryRating.RiskLevelEnum.M),
                new CountryRating().name("Åland Islands").iso2("AX").iso3("ALA").rating(1)
                        .riskLevel(CountryRating.RiskLevelEnum.L));
        assertThat(unmappedCount()).isZero();
        assertThat(warnings()).isEmpty();
    }

    @Test
    void getCountryRatings_shouldOmitUnmappedRowsWithOneWarnAndOneCounterIncrementEach() {
        when(client.fetchCountryRatings()).thenReturn(rows(
                new KnowYourCountryRecord("Afghanistan", "AF", "AFG", "Higher Risk"),
                new KnowYourCountryRecord("Nowhere", "NW", "NWH", "higher risk"),
                new KnowYourCountryRecord("Elsewhere", "EW", "EWH", null)));

        List<CountryRating> ratings = service.getCountryRatings();

        assertThat(ratings).extracting(CountryRating::getIso2).containsExactly("AF");
        assertThat(unmappedCount()).isEqualTo(2.0);
        assertThat(warnings()).hasSize(2);
        assertThat(warnings().get(0).getFormattedMessage())
                .isEqualTo("Unmapped KnowYourCountry risk_level 'higher risk' for iso2 'NW'; country omitted");
    }

    @Test
    void getCountryRatings_shouldReturnAnEmptyListWhenEveryRowIsUnmapped() {
        when(client.fetchCountryRatings()).thenReturn(rows(
                new KnowYourCountryRecord("Nowhere", "NW", "NWH", "Low Risk")));

        assertThat(service.getCountryRatings()).isEmpty();
        assertThat(unmappedCount()).isEqualTo(1.0);
    }

    @Test
    void getCountries_shouldMapVendorRejectionToCLS001() {
        when(client.fetchCountries()).thenReturn(new KnowYourCountryResult.Rejected(401));

        assertThatThrownBy(service::getCountries)
                .isInstanceOf(RiskComplianceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLS001);
    }

    @Test
    void getCountries_shouldMapMalformedToCLS002() {
        when(client.fetchCountries()).thenReturn(new KnowYourCountryResult.Malformed("DecodingException"));

        assertThatThrownBy(service::getCountries)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLS002);
    }

    @Test
    void getCountries_shouldMapUnavailableToCLS003WithTheCause() {
        IllegalStateException cause = new IllegalStateException("timeout");
        when(client.fetchCountries()).thenReturn(new KnowYourCountryResult.Unavailable(cause));

        assertThatThrownBy(service::getCountries)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLS003)
                .hasCause(cause);
    }

    @Test
    void getCountries_shouldWrapAnUnexpectedClientFailureAsCLS004() {
        when(client.fetchCountries()).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(service::getCountries)
                .isInstanceOf(RiskComplianceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLS004);
    }

    @Test
    void getCountryRatings_shouldMapEachFailureToItsCRTCode() {
        when(client.fetchCountryRatings())
                .thenReturn(new KnowYourCountryResult.Rejected(500))
                .thenReturn(new KnowYourCountryResult.Malformed("empty"))
                .thenReturn(new KnowYourCountryResult.Unavailable(new RuntimeException("down")))
                .thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(service::getCountryRatings).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CRT001);
        assertThatThrownBy(service::getCountryRatings).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CRT002);
        assertThatThrownBy(service::getCountryRatings).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CRT003);
        assertThatThrownBy(service::getCountryRatings).hasFieldOrPropertyWithValue("errorCode", ErrorCode.CRT004);
    }

    @Test
    void forLog_shouldNeutralizeControlCharsAndCapLength() {
        assertThat(RiskComplianceService.forLog("Low\r\nFAKE")).isEqualTo("Low__FAKE");
        assertThat(RiskComplianceService.forLog("x".repeat(100))).isEqualTo("x".repeat(64) + "...");
        assertThat(RiskComplianceService.forLog(null)).isNull();
    }
}
