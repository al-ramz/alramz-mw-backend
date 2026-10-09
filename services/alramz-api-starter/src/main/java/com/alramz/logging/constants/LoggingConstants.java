package com.alramz.logging.constants;

/**
 * Centralized logging constants for the Al Ramz API Starter.
 * <p>
 * Keys are intentionally namespaced to avoid collisions with application or
 * framework MDC entries.
 */
public final class LoggingConstants {

    private LoggingConstants() {
    }

    public static final String CORRELATION_ID = "correlationId";
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String TENANT_ID = "tenantId";
    public static final String CLIENT_IP = "clientIp";
    public static final String SERVICE_NAME = "ServiceName";
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String METHOD = "method";
    public final static String URI = "uri";

    public static final String RESPONSE_STATUS = "responseStatus";
    public static final String RESPONSE_TIME_MS = "responseTimeMs";
    public static final String RESPONSE_SIZE_BYTES = "responseSizeBytes";
    public static final String REQUEST_DURATION_MS = "requestDurationMs";
    public static final String EXECUTION_TIME_MS = "executionTimeMs";
    public static final String EXTERNAL_DURATION_MS = "externalDurationMs";

    public static final String DEFAULT_CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String DEFAULT_REQUEST_ID_HEADER = "X-Request-ID";
    public static final String DEFAULT_USER_ID_HEADER = "X-User-ID";
    public static final String DEFAULT_TENANT_ID_HEADER = "X-Tenant-ID";

    public static final String START_TIME_ATTRIBUTE = "alramz.logging.startTime";
    public static final String CORRELATION_ID_ATTRIBUTE = "alramz.logging.correlationId";

    public static final String DEFAULT_SERVICE_NAME = "unknown";

    public static final String LOGGING_PROPERTY_PREFIX = "company.logging";
}
