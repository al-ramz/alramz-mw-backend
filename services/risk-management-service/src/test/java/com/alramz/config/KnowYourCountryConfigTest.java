package com.alramz.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowYourCountryConfigTest {

    @Test
    void maskKey_shouldKeepOnlyTheLastFourCharacters() {
        assertThat(KnowYourCountryConfig.maskKey("nvar_1.0_abcdef0123456789")).isEqualTo("****6789");
    }

    @Test
    void maskKey_shouldFullyMaskAShortKey() {
        assertThat(KnowYourCountryConfig.maskKey("short")).isEqualTo("****");
    }
}
