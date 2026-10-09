package com.alramz.logging.config;

import ch.qos.logback.classic.Level;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized constants and utilities for Seq logging.
 * Eliminates parameter duplication by consolidating all defaults in one place
 * instead of spreading them across SeqAppender, SeqProperties, and logback-spring.xml.
 */
public final class SeqLoggingConfig {

    // HTTP Configuration
    public static final String DEFAULT_URL = "http://localhost:5341";
    public static final String DEFAULT_API_KEY = "";
    public static final String INGEST_PATH = "/ingest/clef";
    public static final String CONTENT_TYPE = "application/vnd.seq.clef; charset=utf-8";
    public static final String HEADER_API_KEY = "X-Seq-ApiKey";

    // Batching & Queueing
    public static final int DEFAULT_BATCH_SIZE = 50;
    public static final int MIN_BATCH_SIZE = 1;
    public static final int DEFAULT_QUEUE_SIZE = 50000;
    public static final int MIN_QUEUE_SIZE = 1;
    public static final int DEFAULT_FLUSH_INTERVAL_MS = 1000;
    public static final int MIN_FLUSH_INTERVAL_MS = 100;

    // Timeouts
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 5000;
    public static final int MIN_TIMEOUT_MS = 100;

    // Retry Configuration
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final int MIN_MAX_RETRIES = 0;
    public static final long BASE_BACKOFF_MS = 1000L;
    public static final long MAX_BACKOFF_MS = 5000L;

    // Circuit Breaker
    public static final boolean DEFAULT_CIRCUIT_BREAKER_ENABLED = false;
    public static final int DEFAULT_FAILURE_THRESHOLD = 3;
    public static final long DEFAULT_COOLDOWN_MS = 10000L;
    public static final int MIN_FAILURE_THRESHOLD = 1;

    // Pre-computed level mapping for fast O(1) lookup
    private static final Map<Integer, String> LEVEL_MAP = buildLevelMap();

    private static Map<Integer, String> buildLevelMap() {
        Map<Integer, String> map = new HashMap<>();
        map.put(Level.ERROR_INT, "Error");
        map.put(Level.WARN_INT, "Warning");
        map.put(Level.INFO_INT, "Information");
        map.put(Level.DEBUG_INT, "Debug");
        map.put(Level.TRACE_INT, "Verbose");
        return Collections.unmodifiableMap(map);
    }

    private static final String DEFAULT_LEVEL = "Information";

    private SeqLoggingConfig() {
        // Utility class - no instantiation
    }

    /**
     * Maps logback Level to Seq level string in O(1) time using hashmap lookup.
     * Falls back to "Information" for null or unmapped levels.
     */
    public static String mapLevel(Level level) {
        if (level == null) {
            return DEFAULT_LEVEL;
        }
        return LEVEL_MAP.getOrDefault(level.toInt(), DEFAULT_LEVEL);
    }

    /**
     * Validates and constrains batch size to acceptable range.
     */
    public static int validateBatchSize(int value) {
        return Math.max(MIN_BATCH_SIZE, value);
    }

    /**
     * Validates and constrains queue size to acceptable range.
     */
    public static int validateQueueSize(int value) {
        return Math.max(MIN_QUEUE_SIZE, value);
    }

    /**
     * Validates and constrains flush interval to acceptable range.
     */
    public static int validateFlushIntervalMs(int value) {
        return Math.max(MIN_FLUSH_INTERVAL_MS, value);
    }

    /**
     * Validates and constrains timeout values to acceptable range.
     */
    public static int validateTimeoutMs(int value) {
        return Math.max(MIN_TIMEOUT_MS, value);
    }

    /**
     * Validates and constrains max retries to acceptable range.
     */
    public static int validateMaxRetries(int value) {
        return Math.max(MIN_MAX_RETRIES, value);
    }

    /**
     * Validates and constrains failure threshold to acceptable range.
     */
    public static int validateFailureThreshold(int value) {
        return Math.max(MIN_FAILURE_THRESHOLD, value);
    }

    /**
     * Validates and constrains cooldown ms to acceptable range.
     */
    public static long validateCooldownMs(long value) {
        return Math.max(0, value);
    }
}
