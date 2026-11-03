package com.alramz.openbee;

import com.alramz.config.DmsProperties;
import com.alramz.config.OpenBeeClientConfig;
import com.alramz.service.CatalogCategory;
import com.alramz.service.ConsumerContext;
import com.alramz.service.FilingCategoryCatalog;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Opt-in checks of the brief section 13 verification items (V1-V6) against an Open Bee TEST environment.
 * Excluded by surefire by default ({@code dms.test.excludedGroups=openbee-live}) and skipped unless all three
 * environment variables are set. Read-only except where a variable explicitly enables an upload. Prints
 * findings (status, shapes, header presence) and never asserts on production. Never point it at production.
 *
 * <pre>
 * DMS_LIVE_OPENBEE_BASE_URL=... DMS_LIVE_OPENBEE_API_KEY=... DMS_LIVE_FILING_CATEGORY_ID=... \
 *   mvn -pl services/document-management-service -am test -Ddms.test.excludedGroups= -Dgroups=openbee-live
 * </pre>
 */
@Tag("openbee-live")
@EnabledIfEnvironmentVariable(named = "DMS_LIVE_OPENBEE_BASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DMS_LIVE_OPENBEE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DMS_LIVE_FILING_CATEGORY_ID", matches = "\\d+")
class OpenBeeLiveVerificationTest {

    private OpenBeeClient client;
    private ConsumerContext consumer;
    private DmsProperties properties;
    private long categoryId;

    @BeforeEach
    void setUp() {
        properties = new DmsProperties();
        properties.getOpenbee().setBaseUrl(System.getenv("DMS_LIVE_OPENBEE_BASE_URL"));
        properties.getOpenbee().setTimeZone(ZoneId.of("Asia/Dubai"));
        OpenBeeClientConfig config = new OpenBeeClientConfig(properties);
        WebClient webClient = config.openBeeWebClient(WebClient.builder(), ExchangeFilterFunction.ofRequestProcessor(
                reactor.core.publisher.Mono::just));
        client = new OpenBeeClient(webClient, config.openBeeCircuitBreaker(CircuitBreakerRegistry.ofDefaults()),
                config.openBeeReadRetry(RetryRegistry.ofDefaults()), config.openBeeWriteRetry(RetryRegistry.ofDefaults()),
                config.openBeeTimeLimiter(TimeLimiterRegistry.ofDefaults()), properties, new MetadataEncoder(properties));
        consumer = new ConsumerContext("live-test", System.getenv("DMS_LIVE_OPENBEE_API_KEY"));
        categoryId = Long.parseLong(System.getenv("DMS_LIVE_FILING_CATEGORY_ID"));
    }

    /** V6: real shape of the categories list (wrapped vs unwrapped) and the configured category's metadata. */
    @Test
    void v6_categoriesShape() {
        OpenBeeCategoryPage page = client.listFilingCategories(consumer, 1, 50);
        long wrapped = page.results() == null ? 0 : page.results().stream().filter(i -> i.filingCategory() != null).count();
        long unwrapped = page.results() == null ? 0 : page.results().stream().filter(i -> i.filingCategory() == null && i.id() != null).count();
        report("V6 categories: wrapped=" + wrapped + " unwrapped=" + unwrapped + " pagination=" + page.pagination());

        FilingCategoryCatalog catalog = new FilingCategoryCatalog(client, properties,
                new com.alramz.config.DmsCacheConfig().dmsCategoryCache(), new com.alramz.config.DmsCacheConfig().dmsListCache(),
                Clock.systemUTC(), Runnable::run);
        Optional<CatalogCategory> category = catalog.find(consumer, categoryId);
        report("V6 category " + categoryId + " usable=" + category.isPresent() + " metadata="
                + category.map(c -> c.metadata().stream().map(m -> m.id() + ":" + m.rawType()).toList()).orElse(List.of()));
        assumeThat(category).isPresent();
    }

    /** V6: idMetadata type in search results; V1 (list form) is visible in the returned values. */
    @Test
    void v6_searchShape() {
        OpenBeeSearchResponse response = client.advancedSearch(consumer,
                new SearchFilterBuilder(new MetadataEncoder(properties)).build(categoryId, List.of(), 1, 1));
        report("V6 search: count=" + (response.pagination() == null ? null : response.pagination().count())
                + " firstItemMetadataCount=" + (response.results() == null || response.results().isEmpty()
                || response.results().get(0).metadata() == null ? 0 : response.results().get(0).metadata().size()));
    }

    /** V4: download headers (Content-Type / Content-Disposition presence) and document-info name shape. */
    @Test
    void v4_downloadHeaders() throws Exception {
        OpenBeeSearchResponse response = client.advancedSearch(consumer,
                new SearchFilterBuilder(new MetadataEncoder(properties)).build(categoryId, List.of(), 1, 1));
        assumeThat(response.results()).isNotEmpty();
        long documentId = response.results().get(0).idDocument();
        OpenBeeFile file = client.downloadFile(consumer, documentId);
        long bytes;
        try (InputStream in = DataBufferUtils.subscriberInputStream(file.body(), 4)) {
            bytes = in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        report("V4 download: contentType=" + file.contentType() + " contentLength=" + file.contentLength()
                + " hasFileName=" + file.fileName().isPresent() + " bytes=" + bytes
                + " documentInfoName=" + client.getDocumentName(consumer, documentId).isPresent());
    }

    /** V4/V5: status for an unknown document. */
    @Test
    void v4_unknownDocument() {
        try {
            client.downloadFile(consumer, Long.MAX_VALUE / 2).body().subscribe().dispose();
            report("V4 unknown document: unexpectedly 2xx");
        } catch (OpenBeeException e) {
            report("V4 unknown document: status=" + e.getHttpStatus() + " codes=" + e.getObCodes());
        }
    }

    private static void report(String finding) {
        System.out.println("[openbee-live] " + finding); // NOPMD SystemPrintln - opt-in diagnostic output
    }
}
