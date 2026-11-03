package com.alramz.service;

import com.alramz.config.DmsProperties;
import com.alramz.logging.util.MDCUtil;
import com.alramz.openbee.OpenBeeCategoryPage;
import com.alramz.openbee.OpenBeeClient;
import com.alramz.openbee.OpenBeeException;
import com.alramz.openbee.OpenBeeFilingCategory;
import com.alramz.openbee.OpenBeeList;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-consumer cache of the filing categories the consumer may save into, with list values (brief 9.1).
 *
 * <ul>
 *   <li>Lazy: loaded on first use, never at startup.</li>
 *   <li>TTL by each entry's {@code loadedAt}; no hard eviction, so the last good entry is served stale (WARN)
 *       when a refresh fails because Open Bee is unavailable.</li>
 *   <li>Reloads are synchronous on the request thread (correlation ID propagates) under a per-consumer lock:
 *       at most one concurrent load per consumer.</li>
 *   <li>Lists are cached per idList and shared across consumers, with the same TTL/stale rules.</li>
 *   <li>Forced refresh (unknown category/metadata ID) at most once per consumer per
 *       {@code dms.cache.forced-refresh-min-interval}.</li>
 * </ul>
 */
@Slf4j
@Component
public class FilingCategoryCatalog {

    static final int ITEMS_PER_PAGE = 50;
    /** "Endpoint" of the TIMEOUT raised for a catalog load that waited or ran too long (maps to DMS203). */
    static final String CATALOG_LOAD = "catalog-load";

    public record CatalogEntry(List<CatalogCategory> categories, Instant loadedAt) {
    }

    public record ListEntry(List<CatalogMetadata.ListValue> values, Instant loadedAt) {
    }

    private final OpenBeeClient openBeeClient;
    private final DmsProperties properties;
    private final Cache<String, CatalogEntry> categoryCache;
    private final Cache<Long, ListEntry> listCache;
    private final Clock clock;
    private final Executor listLoader;

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastForcedRefresh = new ConcurrentHashMap<>();

    public FilingCategoryCatalog(OpenBeeClient openBeeClient,
                                 DmsProperties properties,
                                 @Qualifier("dmsCategoryCache") Cache<String, CatalogEntry> categoryCache,
                                 @Qualifier("dmsListCache") Cache<Long, ListEntry> listCache,
                                 @Qualifier("dmsClock") Clock clock,
                                 @Qualifier("dmsCatalogLoader") Executor listLoader) {
        this.openBeeClient = openBeeClient;
        this.properties = properties;
        this.categoryCache = categoryCache;
        this.listCache = listCache;
        this.clock = clock;
        this.listLoader = listLoader;
    }

    /** The consumer's categories: cached when fresh, otherwise (re)loaded; stale on an Open Bee outage. */
    public List<CatalogCategory> get(ConsumerContext consumer) {
        CatalogEntry entry = categoryCache.getIfPresent(consumer.consumerId());
        if (entry != null && isFresh(entry.loadedAt(), properties.getCache().getCategoriesTtl())) {
            return entry.categories();
        }
        return reload(consumer, false);
    }

    /** The category if the consumer may use it; on a miss, one rate-limited forced refresh, then re-check. */
    public Optional<CatalogCategory> find(ConsumerContext consumer, long filingCategoryId) {
        Optional<CatalogCategory> found = lookup(get(consumer), filingCategoryId);
        if (found.isPresent() || !refreshIfAllowed(consumer)) {
            return found;
        }
        return lookup(get(consumer), filingCategoryId);
    }

    /** Forces a reload unless one was forced for this consumer within the minimum interval. */
    public boolean refreshIfAllowed(ConsumerContext consumer) {
        Instant now = clock.instant();
        Duration minInterval = properties.getCache().getForcedRefreshMinInterval();
        AtomicBoolean allowed = new AtomicBoolean(false);
        lastForcedRefresh.compute(consumer.consumerId(), (id, last) -> {
            if (last == null || !now.isBefore(last.plus(minInterval))) {
                allowed.set(true);
                return now;
            }
            return last;
        });
        if (!allowed.get()) {
            log.debug("Forced catalog refresh for consumer {} skipped (rate limited)", consumer.consumerId());
            return false;
        }
        reload(consumer, true);
        return true;
    }

    /**
     * Background refresh (every minute, {@code DmsCacheConfig}): reloads each cached consumer entry that is within
     * {@code dms.cache.refresh-ahead} of its TTL, so callers keep hitting a fresh cache. A failure leaves the cached
     * entry as it is (callers reload, or get it stale, once it expires). Never throws: an exception would stop
     * the scheduled task.
     */
    public void refreshAhead() {
        Duration ttl = properties.getCache().getCategoriesTtl();
        Duration ahead = properties.getCache().getRefreshAhead();
        for (Map.Entry<String, CatalogEntry> cached : Map.copyOf(categoryCache.asMap()).entrySet()) {
            String consumerId = cached.getKey();
            if (clock.instant().isBefore(cached.getValue().loadedAt().plus(ttl).minus(ahead))) {
                continue;
            }
            DmsProperties.Consumer consumer = properties.getConsumers().get(consumerId);
            if (consumer == null) {
                continue;
            }
            MDCUtil.putCorrelationId(UUID.randomUUID().toString());
            try {
                reload(new ConsumerContext(consumerId, consumer.getApiKey()), true);
                log.debug("Background refresh of the filing categories of consumer {} done", consumerId);
            } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException - must not kill the schedule
                log.warn("Background refresh of the filing categories of consumer {} failed (error={}, correlationId={}); "
                                + "the cached copy is kept", consumerId, e.getClass().getSimpleName(),
                        MDCUtil.getCorrelationId());
            } finally {
                MDC.clear();
            }
        }
    }

    /** Number of consumers with a cached entry (health details). */
    public long cachedCategoryEntries() {
        return categoryCache.estimatedSize();
    }

    private List<CatalogCategory> reload(ConsumerContext consumer, boolean force) {
        String consumerId = consumer.consumerId();
        ReentrantLock lock = locks.computeIfAbsent(consumerId, id -> new ReentrantLock());
        Instant requestedAt = clock.instant();
        if (!acquire(lock)) {
            // Another load for this consumer is still running (slow Open Bee): never queue request threads behind it.
            CatalogEntry current = categoryCache.getIfPresent(consumerId);
            if (current != null) {
                log.warn("Filing categories of consumer {} are still being loaded; serving the cached copy loaded at {} "
                        + "(correlationId={})", consumerId, current.loadedAt(), MDCUtil.getCorrelationId());
                return current.categories();
            }
            log.warn("Filing categories of consumer {} are still being loaded and there is no cached copy "
                    + "(correlationId={})", consumerId, MDCUtil.getCorrelationId());
            throw new OpenBeeException(OpenBeeException.Kind.TIMEOUT, 0, List.of(), CATALOG_LOAD);
        }
        try {
            CatalogEntry current = categoryCache.getIfPresent(consumerId);
            if (current != null && isFresh(current.loadedAt(), properties.getCache().getCategoriesTtl())
                    && (!force || current.loadedAt().isAfter(requestedAt))) {
                return current.categories(); // another thread loaded it while we waited
            }
            try {
                List<CatalogCategory> loaded = load(consumer);
                categoryCache.put(consumerId, new CatalogEntry(loaded, clock.instant()));
                return loaded;
            } catch (OpenBeeException e) {
                if (current != null && e.isUnavailable()) {
                    log.warn("Open Bee unavailable while refreshing filing categories of consumer {}; serving the "
                                    + "cached copy loaded at {} (kind={}, status={}, correlationId={})",
                            consumerId, current.loadedAt(), e.getKind(), e.getHttpStatus(), MDCUtil.getCorrelationId());
                    return current.categories();
                }
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    /** Waits at most {@code dms.cache.lock-wait} for the consumer's load lock. */
    private boolean acquire(ReentrantLock lock) {
        try {
            return lock.tryLock(properties.getCache().getLockWait().toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * One catalog load, abandoned with a TIMEOUT once it has run {@code dms.cache.load-deadline} (checked before
     * each category page and while waiting for the lists; list calls not started yet are cancelled). A TIMEOUT
     * is "unavailable", so the caller keeps serving the cached copy if there is one.
     */
    private List<CatalogCategory> load(ConsumerContext consumer) {
        long deadline = System.nanoTime() + properties.getCache().getLoadDeadline().toNanos();
        List<OpenBeeFilingCategory> raw = new ArrayList<>();
        int page = 1;
        while (true) {
            checkDeadline(deadline);
            OpenBeeCategoryPage response = openBeeClient.listFilingCategories(consumer, page, ITEMS_PER_PAGE);
            if (response.results() != null) {
                response.results().stream()
                        .filter(item -> item != null && item.resolve() != null)
                        .map(OpenBeeCategoryPage.Item::resolve)
                        .forEach(raw::add);
            }
            Integer nbPages = response.pagination() == null ? null : response.pagination().nbPages();
            if (nbPages == null || page >= nbPages) {
                break;
            }
            page++;
        }
        // A list is fetched only for metadata restricted to its existing values: an addNewValue list holds every
        // value ever entered (thousands in practice) and the client may type any value anyway.
        Set<Long> listIds = new LinkedHashSet<>();
        for (OpenBeeFilingCategory category : raw) {
            if (category.metadata() != null) {
                category.metadata().stream()
                        .filter(m -> m != null && m.idList() != null && !Boolean.TRUE.equals(m.addNewValue()))
                        .forEach(m -> listIds.add(m.idList()));
            }
        }
        Map<Long, CompletableFuture<List<CatalogMetadata.ListValue>>> loading = loadLists(consumer, listIds);
        Map<Long, List<CatalogMetadata.ListValue>> lists = new HashMap<>();
        try {
            for (Map.Entry<Long, CompletableFuture<List<CatalogMetadata.ListValue>>> list : loading.entrySet()) {
                lists.put(list.getKey(), await(list.getValue(), deadline));
            }
        } catch (RuntimeException e) {
            loading.values().forEach(future -> future.cancel(false)); // calls not started yet are skipped
            throw e;
        }
        return raw.stream().map(category -> toCatalogCategory(category, lists)).toList();
    }

    private static void checkDeadline(long deadline) {
        if (System.nanoTime() - deadline >= 0) {
            throw new OpenBeeException(OpenBeeException.Kind.TIMEOUT, 0, List.of(), CATALOG_LOAD);
        }
    }

    private static <T> T await(CompletableFuture<T> future, long deadline) {
        try {
            return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw new OpenBeeException(OpenBeeException.Kind.TIMEOUT, 0, List.of(), CATALOG_LOAD, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenBeeException(OpenBeeException.Kind.TIMEOUT, 0, List.of(), CATALOG_LOAD, e);
        } catch (ExecutionException | CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    /** Starts every list lookup on the loader pool (bounded parallelism), carrying the caller's MDC along. */
    private Map<Long, CompletableFuture<List<CatalogMetadata.ListValue>>> loadLists(ConsumerContext consumer,
                                                                                   Set<Long> listIds) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Map<Long, CompletableFuture<List<CatalogMetadata.ListValue>>> loading = new LinkedHashMap<>();
        for (Long listId : listIds) {
            loading.put(listId, CompletableFuture.supplyAsync(() -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                try {
                    return listValues(consumer, listId);
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            }, listLoader));
        }
        return loading;
    }

    private List<CatalogMetadata.ListValue> listValues(ConsumerContext consumer, long listId) {
        ListEntry cached = listCache.getIfPresent(listId);
        if (cached != null && isFresh(cached.loadedAt(), properties.getCache().getListsTtl())) {
            return cached.values();
        }
        try {
            OpenBeeList list = openBeeClient.getList(consumer, listId);
            List<CatalogMetadata.ListValue> values = list.values() == null ? List.of() : list.values().stream()
                    .filter(v -> v != null && v.id() != null)
                    .map(v -> new CatalogMetadata.ListValue(v.id(), v.name(), v.idSublist()))
                    .toList();
            listCache.put(listId, new ListEntry(values, clock.instant()));
            return values;
        } catch (OpenBeeException e) {
            if (cached != null && e.isUnavailable()) {
                log.warn("Open Bee unavailable while refreshing list {}; serving the cached copy loaded at {} "
                        + "(correlationId={})", listId, cached.loadedAt(), MDCUtil.getCorrelationId());
                return cached.values();
            }
            throw e;
        }
    }

    static CatalogCategory toCatalogCategory(OpenBeeFilingCategory category,
                                             Map<Long, List<CatalogMetadata.ListValue>> lists) {
        List<CatalogMetadata> metadata = category.metadata() == null ? List.of() : category.metadata().stream()
                .filter(m -> m != null && m.id() != null)
                .map(m -> {
                    boolean allowNewValue = m.idList() != null && Boolean.TRUE.equals(m.addNewValue());
                    return new CatalogMetadata(m.id(), m.name(), m.type(), MetadataTypeGroup.fromOpenBeeType(m.type()),
                            Boolean.TRUE.equals(m.mandatory()), Boolean.TRUE.equals(m.multiple()),
                            Boolean.TRUE.equals(m.autoincrement()), m.parentMetadata(), m.idList(),
                            m.idList() == null || allowNewValue ? null : lists.getOrDefault(m.idList(), List.of()),
                            allowNewValue);
                })
                .toList();
        OpenBeeFilingCategory.Rule rule = category.rule();
        return new CatalogCategory(category.id(), category.name(),
                rule == null ? null : rule.grammarTree(),
                rule == null ? null : rule.active(),
                rule == null ? null : rule.grammarName(),
                metadata);
    }

    private static Optional<CatalogCategory> lookup(List<CatalogCategory> categories, long id) {
        return categories.stream().filter(c -> c.id() == id).findFirst();
    }

    private boolean isFresh(Instant loadedAt, Duration ttl) {
        return clock.instant().isBefore(loadedAt.plus(ttl));
    }
}
