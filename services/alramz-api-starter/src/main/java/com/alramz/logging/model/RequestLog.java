package com.alramz.logging.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable, structured representation of an incoming HTTP request.
 * <p>
 * Instances are created by {@link com.alramz.logging.filter.RequestLoggingFilter}
 * and serialized either as a human readable log line or as JSON (depending on
 * the active Logback encoder) so that downstream log pipelines can index the
 * individual fields.
 */
public record RequestLog(
        String serviceName,
        String correlationId,
        String requestId,
        String traceId,
        String spanId,
        Instant timestamp,
        String method,
        String uri,
        Map<String, String> queryParams,
        Map<String, String> headers,
        String clientIp,
        String payload
) {

    public RequestLog {
        queryParams = queryParams == null ? Collections.emptyMap() : Map.copyOf(queryParams);
        headers = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String serviceName = "";
        private String correlationId = "";
        private String requestId = "";
        private String traceId = "";
        private String spanId = "";
        private Instant timestamp = Instant.now();
        private String method = "";
        private String uri = "";
        private Map<String, String> queryParams = Collections.emptyMap();
        private Map<String, String> headers = Collections.emptyMap();
        private String clientIp = "";
        private String payload = "";

        public Builder serviceName(String v) { serviceName = v; return this; }
        public Builder correlationId(String v) { correlationId = v; return this; }
        public Builder requestId(String v) { requestId = v; return this; }
        public Builder traceId(String v) { traceId = v; return this; }
        public Builder spanId(String v) { spanId = v; return this; }
        public Builder timestamp(Instant v) { timestamp = v; return this; }
        public Builder method(String v) { method = v; return this; }
        public Builder uri(String v) { uri = v; return this; }
        public Builder queryParams(Map<String, String> v) { queryParams = v; return this; }
        public Builder headers(Map<String, String> v) { headers = v; return this; }
        public Builder clientIp(String v) { clientIp = v; return this; }
        public Builder payload(String v) { payload = v; return this; }
        public RequestLog build() {
            return new RequestLog(serviceName, correlationId, requestId, traceId, spanId,
                    timestamp, method, uri, queryParams, headers, clientIp, payload);
        }
    }
}
