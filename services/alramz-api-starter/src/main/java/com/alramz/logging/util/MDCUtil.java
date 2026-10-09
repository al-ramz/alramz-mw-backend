package com.alramz.logging.util;

import com.alramz.logging.constants.LoggingConstants;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thin wrapper around {@link MDC} providing typed helpers for the standard
 * set of correlation, tracing and request context entries used across the
 * Al Ramz microservices.
 * <p>
 * All helpers are null-safe: {@code null} values are ignored so that optional
 * context (e.g. traceId when OpenTelemetry is not configured) never pollutes
 * the diagnostic context.
 */
public final class MDCUtil {

    private MDCUtil() {
    }

    public static void put(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        MDC.put(key, value);
    }

    public static void putAll(Map<String, String> values) {
        if (values == null) {
            return;
        }
        values.forEach(MDCUtil::put);
    }

    public static String get(String key) {
        if (key == null) {
            return null;
        }
        return MDC.get(key);
    }

    public static void remove(String key) {
        if (key != null) {
            MDC.remove(key);
        }
    }

    public static void clear() {
        MDC.clear();
    }

    public static Map<String, String> getContextMap() {
        return new HashMap<>(MDC.getCopyOfContextMap() == null ? Map.of() : MDC.getCopyOfContextMap());
    }

    // --- typed helpers ------------------------------------------------------

    public static void putCorrelationId(String correlationId) {
        put(LoggingConstants.CORRELATION_ID, correlationId);
    }

    public static void putRequestId(String requestId) {
        put(LoggingConstants.REQUEST_ID, requestId);
    }

    public static void putUserId(String userId) {
        put(LoggingConstants.USER_ID, userId);
    }

    public static void putTenantId(String tenantId) {
        put(LoggingConstants.TENANT_ID, tenantId);
    }

    public static void putClientIp(String clientIp) {
        put(LoggingConstants.CLIENT_IP, clientIp);
    }

    public static void putServiceName(String serviceName) {
        put(LoggingConstants.SERVICE_NAME, serviceName);
    }

    public static void putTraceId(String traceId) {
        put(LoggingConstants.TRACE_ID, traceId);
    }

    public static void putSpanId(String spanId) {
        put(LoggingConstants.SPAN_ID, spanId);
    }

    public static String getCorrelationId() {
        return get(LoggingConstants.CORRELATION_ID);
    }

    /**
     * The current correlation ID as a UUID, for audit rows and response bodies that type it as one. The
     * value comes from a client-controlled header, so a missing or non-UUID value yields a fresh UUID
     * instead of an exception.
     */
    public static UUID getCorrelationIdAsUuid() {
        String correlationId = getCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            try {
                return UUID.fromString(correlationId);
            } catch (IllegalArgumentException ignored) {
                // not a UUID: fall through to a fresh one
            }
        }
        return UUID.randomUUID();
    }

    public static String getTraceId() {
        return get(LoggingConstants.TRACE_ID);
    }

    public static String getSpanId() {
        return get(LoggingConstants.SPAN_ID);
    }

    /**
     * Build a map containing only the standard diagnostic context entries that
     * should travel with every structured log record.
     */
    public static Map<String, String> standardContext() {
        Map<String, String> map = new HashMap<>();
        putIfAbsent(map, LoggingConstants.CORRELATION_ID);
        putIfAbsent(map, LoggingConstants.REQUEST_ID);
        putIfAbsent(map, LoggingConstants.USER_ID);
        putIfAbsent(map, LoggingConstants.TENANT_ID);
        putIfAbsent(map, LoggingConstants.CLIENT_IP);
        putIfAbsent(map, LoggingConstants.SERVICE_NAME);
        putIfAbsent(map, LoggingConstants.TRACE_ID);
        putIfAbsent(map, LoggingConstants.SPAN_ID);
        return map;
    }

    private static void putIfAbsent(Map<String, String> target, String key) {
        String value = MDC.get(key);
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
