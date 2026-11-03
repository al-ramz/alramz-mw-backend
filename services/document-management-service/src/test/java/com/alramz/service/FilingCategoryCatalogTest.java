package com.alramz.service;

import com.alramz.config.DmsCacheConfig;
import com.alramz.config.DmsProperties;
import com.alramz.openbee.OpenBeeCategoryPage;
import com.alramz.openbee.OpenBeeClient;
import com.alramz.openbee.OpenBeeException;
import com.alramz.openbee.OpenBeeFilingCategory;
import com.alramz.openbee.OpenBeeList;
import com.alramz.openbee.OpenBeeWireMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class FilingCategoryCatalogTest {

    private static final ConsumerContext ONBOARDING = new ConsumerContext("onboarding-portal", "test-key-onboarding");
    private static final ConsumerContext CRM = new ConsumerContext("crm", "test-key-crm");

    @Mock
    private OpenBeeClient openBeeClient;

    private MutableClock clock;
    private DmsProperties properties;
    private FilingCategoryCatalog catalog;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-30T08:00:00Z"));
        properties = OpenBeeWireMockSupport.properties("http://localhost:65535");
        DmsCacheConfig cacheConfig = new DmsCacheConfig();
        catalog = new FilingCategoryCatalog(openBeeClient, properties, cacheConfig.dmsCategoryCache(),
                cacheConfig.dmsListCache(), clock, Runnable::run);
    }

    @Test
    void construction_isLazy() {
        verifyNoInteractions(openBeeClient);
        assertThat(catalog.cachedCategoryEntries()).isZero();
        assertThat(new DmsCacheConfig().dmsClock()).isNotNull();
    }

    @Test
    void get_mergesPagesAndFetchesASharedListOnce() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(2, category(42, 7L)));
        when(openBeeClient.listFilingCategories(ONBOARDING, 2, 50)).thenReturn(page(2, category(43, 7L)));
        when(openBeeClient.getList(ONBOARDING, 7)).thenReturn(list7());

        List<CatalogCategory> categories = catalog.get(ONBOARDING);

        assertThat(categories).extracting(CatalogCategory::id).containsExactly(42L, 43L);
        assertThat(categories.get(1).metadata(1L).orElseThrow().listValues())
                .extracting(CatalogMetadata.ListValue::name).containsExactly("Passport", "Emirates ID");
        verify(openBeeClient, times(1)).getList(ONBOARDING, 7);
        assertThat(catalog.cachedCategoryEntries()).isEqualTo(1);
    }

    @Test
    void get_withinTtl_makesNoOpenBeeCall() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(1, category(42, null)));

        catalog.get(ONBOARDING);
        clock.advance(Duration.ofMinutes(14));
        catalog.get(ONBOARDING);

        verify(openBeeClient, times(1)).listFilingCategories(any(), anyInt(), anyInt());
    }

    @Test
    void consumers_areIsolated() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(1, category(42, null)));
        when(openBeeClient.listFilingCategories(CRM, 1, 50)).thenReturn(page(1, category(43, null)));

        assertThat(catalog.get(ONBOARDING)).extracting(CatalogCategory::id).containsExactly(42L);
        assertThat(catalog.get(CRM)).extracting(CatalogCategory::id).containsExactly(43L);
        assertThat(catalog.get(ONBOARDING)).extracting(CatalogCategory::id).containsExactly(42L);

        verify(openBeeClient, times(1)).listFilingCategories(ONBOARDING, 1, 50);
        verify(openBeeClient, times(1)).listFilingCategories(CRM, 1, 50);
    }

    @Test
    void afterTtl_failingRefresh_servesStaleWithWarn(CapturedOutput output) {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50))
                .thenReturn(page(1, category(42, null)))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 500, List.of(), "/c"));

        catalog.get(ONBOARDING);
        clock.advance(Duration.ofMinutes(16));

        assertThat(catalog.get(ONBOARDING)).extracting(CatalogCategory::id).containsExactly(42L);
        verify(openBeeClient, times(2)).listFilingCategories(ONBOARDING, 1, 50);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> output.getOut().contains("serving the cached copy"));
        assertThat(output).contains("serving the cached copy");
    }

    @Test
    void afterTtl_successfulRefresh_replacesTheEntry() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50))
                .thenReturn(page(1, category(42, null)))
                .thenReturn(page(1, category(44, null)));

        catalog.get(ONBOARDING);
        clock.advance(Duration.ofMinutes(16));

        assertThat(catalog.get(ONBOARDING)).extracting(CatalogCategory::id).containsExactly(44L);
    }

    @Test
    void noPriorEntry_failureIsRethrown() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.TIMEOUT, 0, List.of(), "/c"));

        assertThatThrownBy(() -> catalog.get(ONBOARDING)).isInstanceOf(OpenBeeException.class);
    }

    @Test
    void credentialFailure_isNotHiddenByStaleData() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50))
                .thenReturn(page(1, category(42, null)))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 401, List.of("BAD_CREDENTIALS"), "/c"));

        catalog.get(ONBOARDING);
        clock.advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> catalog.get(ONBOARDING))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(401));
    }

    @Test
    void listRefreshFailure_usesTheStaleList() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(1, category(42, 7L)));
        when(openBeeClient.getList(ONBOARDING, 7))
                .thenReturn(list7())
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 503, List.of(), "/l"));

        catalog.get(ONBOARDING);
        clock.advance(Duration.ofMinutes(16));

        assertThat(catalog.get(ONBOARDING).get(0).metadata(1L).orElseThrow().listValues()).hasSize(2);
        verify(openBeeClient, times(2)).getList(ONBOARDING, 7);
    }

    @Test
    void listFailure_withoutStaleList_isRethrown() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(1, category(42, 7L)));
        when(openBeeClient.getList(ONBOARDING, 7))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.TIMEOUT, 0, List.of(), "/l"));

        assertThatThrownBy(() -> catalog.get(ONBOARDING))
                .isInstanceOfSatisfying(OpenBeeException.class,
                        e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.TIMEOUT));
    }

    @Test
    void listNotFound_isRethrownEvenWithStaleList() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(1, category(42, 7L)));
        when(openBeeClient.getList(ONBOARDING, 7))
                .thenReturn(list7())
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 404, List.of(), "/l"));
        catalog.get(ONBOARDING);
        clock.advance(Duration.ofMinutes(16));

        // the category refresh fails with a non-outage error, so it is rethrown rather than served stale
        assertThatThrownBy(() -> catalog.get(ONBOARDING))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
    }

    @Test
    void find_knownCategory_doesNotRefresh() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(1, category(42, null)));

        assertThat(catalog.find(ONBOARDING, 42)).isPresent();

        verify(openBeeClient, times(1)).listFilingCategories(any(), anyInt(), anyInt());
    }

    @Test
    void find_unknownCategory_refreshesOnce_thenRateLimits() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(1, category(42, null)));

        assertThat(catalog.find(ONBOARDING, 99)).isEmpty();
        assertThat(catalog.find(ONBOARDING, 99)).isEmpty();

        verify(openBeeClient, times(2)).listFilingCategories(ONBOARDING, 1, 50); // initial load + one forced refresh

        clock.advance(Duration.ofSeconds(61));
        assertThat(catalog.find(ONBOARDING, 99)).isEmpty();
        verify(openBeeClient, times(3)).listFilingCategories(ONBOARDING, 1, 50);
    }

    @Test
    void find_unknownCategory_seesANewlyGrantedCategoryAfterRefresh() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50))
                .thenReturn(page(1, category(42, null)))
                .thenReturn(page(1, category(42, null), category(45, null)));

        assertThat(catalog.find(ONBOARDING, 45)).map(CatalogCategory::id).contains(45L);
    }

    @Test
    void refreshIfAllowed_isPerConsumer() {
        when(openBeeClient.listFilingCategories(any(), eq(1), eq(50))).thenReturn(page(1, category(42, null)));

        assertThat(catalog.refreshIfAllowed(ONBOARDING)).isTrue();
        assertThat(catalog.refreshIfAllowed(ONBOARDING)).isFalse();
        assertThat(catalog.refreshIfAllowed(CRM)).isTrue();
    }

    @Test
    void concurrentGet_forOneConsumer_loadsOnce() throws Exception {
        CountDownLatch inLoad = new CountDownLatch(1);
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenAnswer(invocation -> {
            inLoad.countDown();
            Thread.sleep(300);
            return page(1, category(42, null));
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<CatalogCategory>> first = pool.submit(() -> catalog.get(ONBOARDING));
            assertThat(inLoad.await(5, TimeUnit.SECONDS)).isTrue();
            Future<List<CatalogCategory>> second = pool.submit(() -> catalog.get(ONBOARDING));

            assertThat(first.get(5, TimeUnit.SECONDS)).hasSize(1);
            assertThat(second.get(5, TimeUnit.SECONDS)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        verify(openBeeClient, times(1)).listFilingCategories(ONBOARDING, 1, 50);
    }

    @Test
    void wrappedAndUnwrappedItems_bothMap() {
        OpenBeeCategoryPage.Item unwrapped = new OpenBeeCategoryPage.Item(null, 46L, "Unwrapped",
                List.of(new OpenBeeFilingCategory.Metadata(9L, "N", "EMAIL", true, false, false, null, null, null)),
                new OpenBeeFilingCategory.Rule(false, "/x", null));
        OpenBeeCategoryPage.Item empty = new OpenBeeCategoryPage.Item(null, null, null, null, null);
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(new OpenBeeCategoryPage(
                new OpenBeeCategoryPage.Pagination(2L, 1),
                List.of(new OpenBeeCategoryPage.Item(category(42, null), null, null, null, null), unwrapped, empty)));

        List<CatalogCategory> categories = catalog.get(ONBOARDING);

        assertThat(categories).extracting(CatalogCategory::id).containsExactly(42L, 46L);
        CatalogCategory mapped = categories.get(1);
        assertThat(mapped.path()).isEqualTo("/x");
        assertThat(mapped.autoFiling()).isFalse();
        assertThat(mapped.metadata(9L).orElseThrow().group()).isEqualTo(MetadataTypeGroup.TEXT);
        assertThat(mapped.metadata(9L).orElseThrow().mandatory()).isTrue();
        assertThat(mapped.metadata(9L).orElseThrow().listValues()).isNull();
        verify(openBeeClient, never()).getList(any(), anyLong());
    }

    @Test
    void missingPaginationAndRule_areTolerated() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(new OpenBeeCategoryPage(null,
                List.of(new OpenBeeCategoryPage.Item(new OpenBeeFilingCategory(47L, "NoRule", null, null),
                        null, null, null, null))));

        CatalogCategory category = catalog.get(ONBOARDING).get(0);

        assertThat(category.path()).isNull();
        assertThat(category.autoFiling()).isNull();
        assertThat(category.fileNamePattern()).isNull();
        assertThat(category.metadata()).isEmpty();
    }

    // ------------------------------------------------------------------ addNewValue lists

    /** A list that allows new values is never fetched; a shared list is still fetched for a restricted metadata. */
    @Test
    void addNewValueLists_areNotFetched() {
        OpenBeeFilingCategory freeOnly = new OpenBeeFilingCategory(50L, "Free", List.of(
                new OpenBeeFilingCategory.Metadata(2L, "REQUEST_ID", "LIST", true, true, false, null, 11887L, true)),
                new OpenBeeFilingCategory.Rule(true, "/x", null));
        OpenBeeFilingCategory sharedList = new OpenBeeFilingCategory(51L, "Mixed", List.of(
                new OpenBeeFilingCategory.Metadata(3L, "FREE", "LIST", false, false, false, null, 7L, true),
                new OpenBeeFilingCategory.Metadata(4L, "PICK", "LIST", false, false, false, null, 7L, false)),
                new OpenBeeFilingCategory.Rule(true, "/y", null));
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(1, freeOnly, sharedList));
        when(openBeeClient.getList(ONBOARDING, 7)).thenReturn(list7());

        List<CatalogCategory> categories = catalog.get(ONBOARDING);

        CatalogMetadata requestId = categories.get(0).metadata(2L).orElseThrow();
        assertThat(requestId.allowNewValue()).isTrue();
        assertThat(requestId.listValues()).isNull();
        assertThat(requestId.listId()).isEqualTo(11887L);
        assertThat(categories.get(1).metadata(3L).orElseThrow().listValues()).isNull();
        assertThat(categories.get(1).metadata(4L).orElseThrow().allowNewValue()).isFalse();
        assertThat(categories.get(1).metadata(4L).orElseThrow().listValues()).hasSize(2);
        verify(openBeeClient, never()).getList(ONBOARDING, 11887L);
        verify(openBeeClient, times(1)).getList(ONBOARDING, 7L);
    }

    // ------------------------------------------------------------------ parallel list loading

    /** Two list calls must be in flight at the same time, each seeing the caller's correlation ID. */
    @Test
    void lists_loadInParallel_withTheCallersMdc() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            FilingCategoryCatalog parallel = new FilingCategoryCatalog(openBeeClient, properties,
                    new DmsCacheConfig().dmsCategoryCache(), new DmsCacheConfig().dmsListCache(), clock, pool);
            when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50))
                    .thenReturn(page(1, category(42, 7L), category(43, 8L)));
            CountDownLatch bothStarted = new CountDownLatch(2);
            List<String> seenCorrelationIds = new java.util.concurrent.CopyOnWriteArrayList<>();
            when(openBeeClient.getList(eq(ONBOARDING), anyLong())).thenAnswer(invocation -> {
                seenCorrelationIds.add(String.valueOf(org.slf4j.MDC.get("correlationId")));
                bothStarted.countDown();
                assertThat(bothStarted.await(5, TimeUnit.SECONDS)).as("list calls overlap").isTrue();
                return list7();
            });
            org.slf4j.MDC.put("correlationId", "corr-parallel");
            try {
                assertThat(parallel.get(ONBOARDING)).extracting(CatalogCategory::id).containsExactly(42L, 43L);
            } finally {
                org.slf4j.MDC.clear();
            }
            assertThat(seenCorrelationIds).containsExactly("corr-parallel", "corr-parallel");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void lists_failureOnAPoolThread_isRethrownAsIs() {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            FilingCategoryCatalog parallel = new FilingCategoryCatalog(openBeeClient, properties,
                    new DmsCacheConfig().dmsCategoryCache(), new DmsCacheConfig().dmsListCache(), clock, pool);
            when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(1, category(42, 7L)));
            OpenBeeException failure = new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 401, List.of(), "/l");
            when(openBeeClient.getList(ONBOARDING, 7)).thenThrow(failure);

            assertThatThrownBy(() -> parallel.get(ONBOARDING)).isSameAs(failure);
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ background refresh

    @Test
    void refreshAhead_reloadsOnlyEntriesCloseToTheirTtl() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50))
                .thenReturn(page(1, category(42, null)))
                .thenReturn(page(1, category(42, null), category(43, null)));
        catalog.get(ONBOARDING);

        clock.advance(Duration.ofMinutes(9)); // TTL 15m, refresh-ahead 5m: not yet
        catalog.refreshAhead();
        verify(openBeeClient, times(1)).listFilingCategories(ONBOARDING, 1, 50);

        clock.advance(Duration.ofMinutes(2)); // 11m: within 5m of the TTL
        catalog.refreshAhead();
        verify(openBeeClient, times(2)).listFilingCategories(ONBOARDING, 1, 50);

        clock.advance(Duration.ofMinutes(10)); // 10m after the background reload: still fresh, no caller wait
        assertThat(catalog.get(ONBOARDING)).extracting(CatalogCategory::id).containsExactly(42L, 43L);
        verify(openBeeClient, times(2)).listFilingCategories(ONBOARDING, 1, 50);
    }

    @Test
    void refreshAhead_failure_keepsTheEntryAndNeverThrows(CapturedOutput output) {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50))
                .thenReturn(page(1, category(42, null)))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 401, List.of(), "/c"));
        catalog.get(ONBOARDING);
        clock.advance(Duration.ofMinutes(12));

        catalog.refreshAhead();

        assertThat(catalog.get(ONBOARDING)).extracting(CatalogCategory::id).containsExactly(42L);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> output.getOut().contains("Background refresh of the filing categories of consumer onboarding-portal failed"));
        assertThat(output).contains("Background refresh of the filing categories of consumer onboarding-portal failed");
        assertThat(org.slf4j.MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    // ------------------------------------------------------------------ slow Open Bee protections

    /** While one request's load hangs on Open Bee, others wait at most lock-wait: stale copy, else TIMEOUT (DMS203). */
    @Test
    void lockWait_otherRequestsNeverQueueBehindASlowLoad() throws Exception {
        properties.getCache().setLockWait(Duration.ofMillis(200));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch loading = new CountDownLatch(1);
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenAnswer(invocation -> {
            loading.countDown();
            release.await(10, TimeUnit.SECONDS);
            return page(1, category(42, null));
        });
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            Future<List<CatalogCategory>> slow = other.submit(() -> catalog.get(ONBOARDING));
            assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();

            long started = System.nanoTime();
            assertThatThrownBy(() -> catalog.get(ONBOARDING)).isInstanceOfSatisfying(OpenBeeException.class, e -> {
                assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.TIMEOUT);
                assertThat(e.isUnavailable()).isTrue();
            });
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));

            release.countDown();
            assertThat(slow.get(5, TimeUnit.SECONDS)).extracting(CatalogCategory::id).containsExactly(42L);
        } finally {
            release.countDown();
            other.shutdownNow();
        }
    }

    @Test
    void lockWait_servesTheCachedCopyWhileAnotherLoadRuns() throws Exception {
        properties.getCache().setLockWait(Duration.ofMillis(200));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch loading = new CountDownLatch(1);
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50))
                .thenReturn(page(1, category(42, null)))
                .thenAnswer(invocation -> {
                    loading.countDown();
                    release.await(10, TimeUnit.SECONDS);
                    return page(1, category(42, null), category(43, null));
                });
        catalog.get(ONBOARDING);
        clock.advance(Duration.ofMinutes(16));
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            other.submit(() -> catalog.get(ONBOARDING));
            assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(catalog.get(ONBOARDING)).extracting(CatalogCategory::id).containsExactly(42L);
        } finally {
            release.countDown();
            other.shutdownNow();
        }
    }

    @Test
    void loadDeadline_stopsBetweenCategoryPages() {
        properties.getCache().setLoadDeadline(Duration.ofMillis(100));
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenAnswer(invocation -> {
            Thread.sleep(300);
            return page(2, category(42, null));
        });

        assertThatThrownBy(() -> catalog.get(ONBOARDING)).isInstanceOfSatisfying(OpenBeeException.class,
                e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.TIMEOUT));
        verify(openBeeClient, never()).listFilingCategories(ONBOARDING, 2, 50);
    }

    /** Lists still queued when the deadline passes are cancelled; the previous copy stays in use. */
    @Test
    void loadDeadline_cancelsQueuedListsAndKeepsTheCachedCopy() throws Exception {
        ExecutorService onePool = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        try {
            FilingCategoryCatalog slow = new FilingCategoryCatalog(openBeeClient, properties,
                    new DmsCacheConfig().dmsCategoryCache(), new DmsCacheConfig().dmsListCache(), clock, onePool);
            when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50))
                    .thenReturn(page(1, category(42, null)))
                    .thenReturn(page(1, category(42, 7L), category(43, 8L), category(44, 9L)));
            when(openBeeClient.getList(ONBOARDING, 7)).thenAnswer(invocation -> {
                release.await(10, TimeUnit.SECONDS);
                return list7();
            });
            slow.get(ONBOARDING);
            clock.advance(Duration.ofMinutes(16));
            properties.getCache().setLoadDeadline(Duration.ofMillis(200));

            assertThat(slow.get(ONBOARDING)).extracting(CatalogCategory::id).containsExactly(42L);

            release.countDown();
            onePool.shutdown();
            assertThat(onePool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            verify(openBeeClient, never()).getList(ONBOARDING, 8);
            verify(openBeeClient, never()).getList(ONBOARDING, 9);
        } finally {
            release.countDown();
            onePool.shutdownNow();
        }
    }

    @Test
    void refreshAhead_skipsConsumersNoLongerConfigured() {
        when(openBeeClient.listFilingCategories(ONBOARDING, 1, 50)).thenReturn(page(1, category(42, null)));
        catalog.get(ONBOARDING);
        properties.getConsumers().remove("onboarding-portal");
        clock.advance(Duration.ofMinutes(12));

        catalog.refreshAhead();

        verify(openBeeClient, times(1)).listFilingCategories(ONBOARDING, 1, 50);
    }

    private static OpenBeeCategoryPage page(int nbPages, OpenBeeFilingCategory... categories) {
        return new OpenBeeCategoryPage(new OpenBeeCategoryPage.Pagination((long) categories.length, nbPages),
                java.util.Arrays.stream(categories).map(c -> new OpenBeeCategoryPage.Item(c, null, null, null, null)).toList());
    }

    private static OpenBeeFilingCategory category(long id, Long idList) {
        return new OpenBeeFilingCategory(id, "Category " + id, List.of(
                new OpenBeeFilingCategory.Metadata(1L, "TYPE", idList == null ? "VARCHAR" : "LIST", true, false, false,
                        null, idList, false)),
                new OpenBeeFilingCategory.Rule(true, "/Clients/[CLIENT_ID]", "NAME_[CLIENT_ID]"));
    }

    private static OpenBeeList list7() {
        return new OpenBeeList(7L, "Document types", List.of(new OpenBeeList.Value(101L, "Passport", null),
                new OpenBeeList.Value(102L, "Emirates ID", null), new OpenBeeList.Value(null, "no id", null)));
    }
}
