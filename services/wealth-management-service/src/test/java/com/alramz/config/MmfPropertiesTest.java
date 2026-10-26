package com.alramz.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MmfPropertiesTest {

    @Test
    void shouldBindCommaSeparatedClientIds() {
        MmfProperties properties = bind(Map.of("mmf.custodian.client-ids", "2001214,2001215"));

        assertThat(properties.getCustodian().getClientIds()).containsExactly("2001214", "2001215");
    }

    @Test
    void emptyValue_shouldBindToEmptyList() {
        MmfProperties properties = bind(Map.of("mmf.custodian.client-ids", ""));

        assertThat(properties.getCustodian().getClientIds()).isEmpty();
    }

    @Test
    void absentValue_shouldDefaultToEmptyList() {
        assertThat(new MmfProperties().getCustodian().getClientIds()).isEmpty();
    }

    private static MmfProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate(MmfProperties.PREFIX, MmfProperties.class);
    }
}
