package com.alramz.logging.otel;

/**
 * Abstraction that exposes the current OpenTelemetry {@code traceId} and
 * {@code spanId}, if an OpenTelemetry SDK context is populated on the current
 * thread.
 * <p>
 * Two implementations are auto-registered by
 * {@link com.alramz.logging.config.LoggingAutoConfiguration}: a no-op variant
 * (always present) and an OpenTelemetry variant (present only when
 * {@code io.opentelemetry.api.trace.Span} is on the classpath). The
 * orchestrator picks the supported one, so the framework keeps working when the
 * SDK is absent and never requires an external observability service.
 */
public interface TraceContextExtractor {

    /**
     * @return the current trace id or {@code null} when no SDK is configured.
     */
    String traceId();

    /**
     * @return the current span id or {@code null} when no SDK is configured.
     */
    String spanId();

    /**
     * @return {@code true} when this extractor is actually backed by an SDK.
     */
    boolean isSupported();
}
