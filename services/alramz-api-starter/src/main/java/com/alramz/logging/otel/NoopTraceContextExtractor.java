package com.alramz.logging.otel;

/**
 * No-op {@link TraceContextExtractor} used when no OpenTelemetry API/SKD is on
 * the classpath. Always reports {@code null} values so that trace/span ids are
 * simply omitted from logs without breaking the framework.
 */
public final class NoopTraceContextExtractor implements TraceContextExtractor {

    @Override
    public String traceId() {
        return null;
    }

    @Override
    public String spanId() {
        return null;
    }

    @Override
    public boolean isSupported() {
        return false;
    }
}
