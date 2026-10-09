package com.alramz.logging.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * Reads the current trace/span ids from the OpenTelemetry thread-local context
 * ({@code Span.current()}). Conditionally registered only when the
 * OpenTelemetry API class is present, so the framework never breaks when the
 * SDK is absent. No external collector or service is required to populate the
 * context ids: they are resolved purely from the in-process SDK context.
 */
public final class OpenTelemetryTraceContextExtractor implements TraceContextExtractor {

    @Override
    public String traceId() {
        try {
            SpanContext ctx = Span.current().getSpanContext();
            if (ctx != null && ctx.isValid()) {
                return ctx.getTraceId();
            }
        } catch (Throwable t) {
            // Defensive: OTel may not be fully initialized or context is invalid.
            return null;
        }
        return null;
    }

    @Override
    public String spanId() {
        try {
            SpanContext ctx = Span.current().getSpanContext();
            if (ctx != null && ctx.isValid()) {
                return ctx.getSpanId();
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    @Override
    public boolean isSupported() {
        return true;
    }
}
