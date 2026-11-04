package com.alramz.knowyourcountry;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RiskBandTest {

    @ParameterizedTest
    @CsvSource({
            "Higher Risk, HIGH, 20, H",
            "Medium-High Risk, HIGH, 20, H",
            "Medium Risk, MEDIUM, 9, M",
            "Medium-Low Risk, MEDIUM, 9, M",
            "Lower Risk, LOW, 1, L"
    })
    void fromVendorLabel_shouldMapEachExactLabelToItsBand(String label, RiskBand band, int rating, String code) {
        assertThat(RiskBand.fromVendorLabel(label)).contains(band);
        assertThat(band.rating()).isEqualTo(rating);
        assertThat(band.code()).isEqualTo(code);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"higher risk", "Higher Risk ", " Lower Risk", "Low Risk", "HIGHER RISK"})
    void fromVendorLabel_shouldReturnEmptyForAnythingElse(String label) {
        assertThat(RiskBand.fromVendorLabel(label)).isEmpty();
    }
}
