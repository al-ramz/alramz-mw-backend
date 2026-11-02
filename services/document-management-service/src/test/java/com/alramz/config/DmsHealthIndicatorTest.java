package com.alramz.config;

import com.alramz.openbee.OpenBeeCategoryPage;
import com.alramz.openbee.OpenBeeClient;
import com.alramz.openbee.OpenBeeWireMockSupport;
import com.alramz.service.ConsumerContext;
import com.alramz.service.FilingCategoryCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DmsHealthIndicatorTest {

    @Mock
    private OpenBeeClient openBeeClient;

    @Test
    void alwaysUpWithCountsOnly_andNeverCallsOpenBee() {
        DmsProperties properties = OpenBeeWireMockSupport.properties("http://localhost:65535");
        DmsCacheConfig cacheConfig = new DmsCacheConfig();
        FilingCategoryCatalog catalog = new FilingCategoryCatalog(openBeeClient, properties,
                cacheConfig.dmsCategoryCache(), cacheConfig.dmsListCache(), Clock.systemUTC(), Runnable::run);
        DmsHealthIndicator indicator = new DmsHealthIndicator(properties, catalog);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsOnlyKeys("configuredConsumers", "cachedCategoryEntries");
        assertThat(health.getDetails().get("configuredConsumers")).isEqualTo(2);
        assertThat(health.getDetails().get("cachedCategoryEntries")).isEqualTo(0L);
        assertThat(health.getDetails().toString()).doesNotContain("test-key", "crm", "onboarding-portal", "localhost");
        verifyNoInteractions(openBeeClient);
    }

    @Test
    void reportsCachedEntries() {
        DmsProperties properties = OpenBeeWireMockSupport.properties("http://localhost:65535");
        DmsCacheConfig cacheConfig = new DmsCacheConfig();
        FilingCategoryCatalog catalog = new FilingCategoryCatalog(openBeeClient, properties,
                cacheConfig.dmsCategoryCache(), cacheConfig.dmsListCache(), Clock.systemUTC(), Runnable::run);
        ConsumerContext crm = new ConsumerContext("crm", "test-key-crm");
        when(openBeeClient.listFilingCategories(crm, 1, 50))
                .thenReturn(new OpenBeeCategoryPage(new OpenBeeCategoryPage.Pagination(0L, 1), List.of()));
        catalog.get(crm);

        Health health = new DmsHealthIndicator(properties, catalog).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("cachedCategoryEntries")).isEqualTo(1L);
        verify(openBeeClient).listFilingCategories(crm, 1, 50);
    }
}
