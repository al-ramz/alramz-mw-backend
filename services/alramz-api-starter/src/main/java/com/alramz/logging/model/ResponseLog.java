package com.alramz.logging.model;

import java.time.Instant;

/**
 * Immutable, structured representation of an outgoing HTTP response.
 */
public record ResponseLog(
        String serviceName,
        String correlationId,
        String traceId,
        String spanId,
        Instant timestamp,
        int status,
        String statusMessage,
        long responseTimeMs,
        long responseSizeBytes,
        String payload
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String serviceName = "";
        private String correlationId = "";
        private String traceId = "";
        private String spanId = "";
        private Instant timestamp = Instant.now();
        private int status = 0;
        private String statusMessage = "";
        private long responseTimeMs = 0L;
        private long responseSizeBytes = 0L;
        private String payload = "";

        public Builder serviceName(String v) { serviceName = v; return this; }
        public Builder correlationId(String v) { correlationId = v; return this; }
        public Builder traceId(String v) { traceId = v; return this; }
        public Builder spanId(String v) { spanId = v; return this; }
        public Builder timestamp(Instant v) { timestamp = v; return this; }
        public Builder status(int v) { status = v; return this; }
        public Builder statusMessage(String v) { statusMessage = v; return this; }
        public Builder responseTimeMs(long v) { responseTimeMs = v; return this; }
        public Builder responseSizeBytes(long v) { responseSizeBytes = v; return this; }
        public Builder payload(String v) { payload = v; return this; }
        public ResponseLog build() {
            return new ResponseLog(serviceName, correlationId, traceId, spanId, timestamp,
                    status, statusMessage, responseTimeMs, responseSizeBytes, payload);
        }
    }
}
