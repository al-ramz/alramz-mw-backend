package com.alramz.config;

import com.alramz.service.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CacheWarmupRunnerTest {

    private final RedisCacheService service = mock(RedisCacheService.class);

    private static CompanyRedisProperties.Cache.CacheMapping mapping(String strategy, boolean enabled) {
        CompanyRedisProperties.Cache.CacheMapping m = new CompanyRedisProperties.Cache.CacheMapping();
        m.setReloadStrategy(strategy);
        m.setEnabled(enabled);
        return m;
    }

    private void run(CompanyRedisProperties props) throws Exception {
        ApplicationRunner runner = new CacheWarmupRunner().warmupRunner(service, props);
        runner.run(new DefaultApplicationArguments());
    }

    @Test
    void warmupRunner_shouldWarmOnlyEnabledStartupMappings() throws Exception {
        Map<String, CompanyRedisProperties.Cache.CacheMapping> mappings = new LinkedHashMap<>();
        mappings.put("countries", mapping("STARTUP", true));
        mappings.put("banks", mapping("scheduled", true));
        mappings.put("cities", mapping("startup", false));
        CompanyRedisProperties props = new CompanyRedisProperties();
        props.getCache().setMappings(mappings);

        run(props);

        verify(service).getAllEntries("countries");
        verify(service, never()).getAllEntries("banks");
        verify(service, never()).getAllEntries("cities");
    }

    @Test
    void warmupRunner_shouldDoNothingWhenNoMappingsConfigured() throws Exception {
        run(new CompanyRedisProperties());

        verifyNoInteractions(service);
    }

    @Test
    void warmupRunner_shouldContinueWhenWarmingAMappingFails() throws Exception {
        Map<String, CompanyRedisProperties.Cache.CacheMapping> mappings = new LinkedHashMap<>();
        mappings.put("countries", mapping("startup", true));
        mappings.put("banks", mapping("startup", true));
        CompanyRedisProperties props = new CompanyRedisProperties();
        props.getCache().setMappings(mappings);
        when(service.getAllEntries("countries")).thenThrow(new IllegalStateException("db down"));

        assertThatNoException().isThrownBy(() -> run(props));
        verify(service).getAllEntries("banks");
    }
}
