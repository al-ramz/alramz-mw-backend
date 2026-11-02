package com.alramz.config;

import com.alramz.service.FilingCategoryCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Config and cache health only. Open Bee has no safe key-less endpoint (brief 9.2), so this never calls it and
 * never reports DOWN because Open Bee is down. Details are counts only: no consumer IDs, keys or URLs.
 */
@Component
@RequiredArgsConstructor
public class DmsHealthIndicator implements HealthIndicator {

    private final DmsProperties properties;
    private final FilingCategoryCatalog catalog;

    @Override
    public Health health() {
        return Health.up()
                .withDetail("configuredConsumers", properties.getConsumers().size())
                .withDetail("cachedCategoryEntries", catalog.cachedCategoryEntries())
                .build();
    }
}
