package com.alramz.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CacheMappingPropertiesTest {

    @Test
    void mapping_shouldBeEnabledByDefault() {
        assertThat(new CacheMappingProperties.CacheMapping().isEnabled()).isTrue();
    }

    @Test
    void setters_shouldBindMappingValues() {
        CacheMappingProperties.CacheMapping mapping = new CacheMappingProperties.CacheMapping();
        mapping.setTable("countries");
        mapping.setCacheKey("ref:countries");
        mapping.setReloadStrategy("scheduled");
        mapping.setTtl("30M");
        mapping.setEnabled(false);
        CacheMappingProperties props = new CacheMappingProperties();
        props.setMappings(Map.of("countries", mapping));

        CacheMappingProperties.CacheMapping bound = props.getMappings().get("countries");
        assertThat(bound.getTable()).isEqualTo("countries");
        assertThat(bound.getCacheKey()).isEqualTo("ref:countries");
        assertThat(bound.getReloadStrategy()).isEqualTo("scheduled");
        assertThat(bound.getTtl()).isEqualTo("30M");
        assertThat(bound.isEnabled()).isFalse();
    }
}
