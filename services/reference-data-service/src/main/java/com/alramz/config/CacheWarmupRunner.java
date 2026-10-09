package com.alramz.config;

import com.alramz.config.CompanyRedisProperties;
import com.alramz.service.RedisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheWarmupRunner {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmupRunner.class);

    @Bean
    public ApplicationRunner warmupRunner(RedisCacheService redisCacheService,
                                                CompanyRedisProperties companyRedisProperties) {
        return args -> {
            if (companyRedisProperties.getCache().getMappings() == null) {
                return;
            }

            companyRedisProperties.getCache().getMappings().forEach((name, mapping) -> {
                if (!mapping.isEnabled()) {
                    return;
                }

                if (!"startup".equalsIgnoreCase(mapping.getReloadStrategy())) {
                    return;
                }

                try {
                    log.info("Warming cache for mapping: {}", name);
                    redisCacheService.getAllEntries(name);
                    log.info("Cache warmed successfully for mapping: {}", name);
                } catch (Exception e) {
                    log.error("Failed to warm cache for mapping {}: {}", name, e.getMessage(), e);
                }
            });
        };
    }
}