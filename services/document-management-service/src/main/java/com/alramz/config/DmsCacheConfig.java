package com.alramz.config;

import com.alramz.service.FilingCategoryCatalog;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory catalog storage. No expireAfterWrite/refreshAfterWrite: TTL is checked against each entry's
 * loadedAt, so the last good entry survives its TTL and can be served stale when Open Bee is down. Loading is
 * lazy (on first request), never at startup; after that, a background task reloads each cached entry before
 * its TTL so callers do not wait for a reload.
 */
@Slf4j
@Configuration
public class DmsCacheConfig {

    static final long MAX_CONSUMERS = 1_000;
    static final long MAX_LISTS = 10_000;
    static final long REFRESH_CHECK_SECONDS = 60;

    @Bean(name = "dmsCategoryCache")
    public Cache<String, FilingCategoryCatalog.CatalogEntry> dmsCategoryCache() {
        return Caffeine.newBuilder().maximumSize(MAX_CONSUMERS).build();
    }

    /** Keyed by Open Bee idList, shared across consumers (list values are not permission-scoped). */
    @Bean(name = "dmsListCache")
    public Cache<Long, FilingCategoryCatalog.ListEntry> dmsListCache() {
        return Caffeine.newBuilder().maximumSize(MAX_LISTS).build();
    }

    @Bean(name = "dmsClock")
    public Clock dmsClock() {
        return Clock.systemUTC();
    }

    /** Parallel Open Bee list calls while loading the catalog (each call takes ~2 s on the live Open Bee). */
    @Bean(name = "dmsCatalogLoader", destroyMethod = "shutdownNow")
    public ExecutorService dmsCatalogLoader(DmsProperties properties) {
        return Executors.newFixedThreadPool(properties.getCache().getListLoadConcurrency(),
                daemonThreads("dms-catalog-load-"));
    }

    /**
     * Runs {@link FilingCategoryCatalog#refreshAhead()} every minute. Own executor rather than {@code @Scheduled}:
     * scheduling is not enabled in this repo, and enabling it would also start the starter's cron jobs.
     */
    @Bean(name = "dmsCatalogRefresher", destroyMethod = "shutdownNow")
    public ScheduledExecutorService dmsCatalogRefresher(FilingCategoryCatalog catalog) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                daemonThreads("dms-catalog-refresh-"));
        scheduler.scheduleWithFixedDelay(() -> refreshSafely(catalog), REFRESH_CHECK_SECONDS, REFRESH_CHECK_SECONDS,
                TimeUnit.SECONDS);
        return scheduler;
    }

    /** Anything escaping a scheduled run would silently cancel every later run: log it and keep the schedule. */
    static void refreshSafely(FilingCategoryCatalog catalog) {
        try {
            catalog.refreshAhead();
        } catch (Throwable t) { // NOPMD AvoidCatchingThrowable - must not end the schedule (refreshAhead already catches RuntimeException)
            log.error("Background refresh of filing categories failed (error={}); retrying in {} s",
                    t.getClass().getName(), REFRESH_CHECK_SECONDS);
        }
    }

    static ThreadFactory daemonThreads(String prefix) {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
