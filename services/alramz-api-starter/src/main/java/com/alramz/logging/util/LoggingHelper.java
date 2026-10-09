package com.alramz.logging.util;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletResponse;
import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.model.RequestLog;
import com.alramz.logging.model.ResponseLog;
import com.alramz.logging.otel.TraceContextExtractor;
import com.alramz.logging.constants.LoggingConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Orchestrating helper used by the filters and interceptors. It is the single
 * entry point for resolving the service name, populating the diagnostic
 * context (MDC) from an HTTP request and for safely reading masked request
 * bodies and query/header maps.
 */
public class LoggingHelper {

    private final String serviceName;
    private final LoggingProperties properties;
    private final TraceContextExtractor traceContextExtractor;
    private final boolean maskingEnabled;

    public LoggingHelper(Environment environment, LoggingProperties properties,
                         List<TraceContextExtractor> traceContextExtractors) {
        this.serviceName = environment.getProperty("spring.application.name", LoggingConstants.DEFAULT_SERVICE_NAME);
        this.properties = properties;
        this.traceContextExtractor = resolveExtractor(traceContextExtractors);
        this.maskingEnabled = properties.getMasking().isEnabled(); // NOPMD LawOfDemeter
    }

    private static TraceContextExtractor resolveExtractor(List<TraceContextExtractor> extractors) {
        if (extractors != null) {
            for (TraceContextExtractor e : extractors) {
                if (e.isSupported()) {
                    return e;
                }
            }
        }
        return new com.alramz.logging.otel.NoopTraceContextExtractor();
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getCorrelationIdHeader() {
        return properties.getCorrelationId().getHeader(); // NOPMD LawOfDemeter
    }

    public boolean isMaskingEnabled() {
        return maskingEnabled;
    }

    public TraceContextExtractor getTraceContextExtractor() {
        return traceContextExtractor;
    }

    /**
     * Populate the MDC with the standard diagnostic context entries derived
     * from the current request. Correlation id must already be present.
     */
    public void populateMdc(HttpServletRequest request) {
        MDCUtil.putServiceName(serviceName);
        MDCUtil.putClientIp(extractClientIp(request));
        MDCUtil.putUserId(resolveHeader(request, properties.getCorrelationId().getUserIdHeader())); // NOPMD LawOfDemeter
        MDCUtil.putTenantId(resolveHeader(request, properties.getCorrelationId().getTenantIdHeader())); // NOPMD LawOfDemeter
        MDCUtil.putRequestId(resolveHeader(request, properties.getCorrelationId().getRequestIdHeader())); // NOPMD LawOfDemeter
        MDCUtil.put(LoggingConstants.URI, request.getRequestURI());
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            MDCUtil.put("queryString", query);
        }
        MDCUtil.putTraceId(traceContextExtractor.traceId());
        MDCUtil.putSpanId(traceContextExtractor.spanId());
    }

    private String resolveHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return maskingEnabled && LogMaskingUtil.isSensitiveKey(headerName)
                ? LogMaskingUtil.maskValue(headerName, value)
                : value;
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    public Map<String, String> toQueryParams(HttpServletRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        if (request == null) {
            return map;
        }
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                String value = maskingEnabled ? LogMaskingUtil.mask(values[0]) : values[0];
                map.put(key, value);
            }
        });
        return map;
    }

    public Map<String, String> toHeaders(HttpServletRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        if (request == null) {
            return map;
        }
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return map;
        }
        while (names.hasMoreElements()) {
            String headerName = names.nextElement();
            List<String> values = Collections.list(request.getHeaders(headerName));
            String value = LogMaskingUtil.maskHeader(headerName, values);
            map.put(headerName, value);
        }
        return map;
    }

    public String extractPayload(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        ContentCachingRequestWrapper caching = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (caching == null) {
            return null;
        }
        byte[] content = caching.getContentAsByteArray();
        if (content == null || content.length == 0) {
            return null;
        }
        String raw = new String(content, StandardCharsets.UTF_8);
        String limited = raw.length() > properties.getRequest().getMaxPayloadLength() // NOPMD LawOfDemeter
                ? raw.substring(0, properties.getRequest().getMaxPayloadLength()) + "...[truncated]" // NOPMD LawOfDemeter
                : raw;
        return maskingEnabled ? LogMaskingUtil.mask(limited) : limited;
    }

    /**
     * Build a {@link RequestLog} for the current request. Payload is only
     * populated when request payload logging is enabled, and only if the
     * request has been wrapped in a {@link ContentCachingRequestWrapper}.
     */
    public RequestLog buildRequestLog(HttpServletRequest request, String correlationId) {
        if (request == null) {
            return null;
        }
        LoggingProperties.RequestProperties requestProps = properties.getRequest();
        Map<String, String> headers = requestProps.isIncludeHeaders()
                ? toHeaders(request) : Collections.emptyMap();
        String payload = requestProps.isIncludePayload() ? extractPayload(request) : null;
        return RequestLog.builder()
                .serviceName(serviceName)
                .correlationId(correlationId)
                .requestId(MDCUtil.get(LoggingConstants.REQUEST_ID))
                .traceId(MDCUtil.get(LoggingConstants.TRACE_ID))
                .spanId(MDCUtil.get(LoggingConstants.SPAN_ID))
                .timestamp(java.time.Instant.now())
                .method(request.getMethod())
                .uri(request.getRequestURI())
                .queryParams(toQueryParams(request))
                .headers(headers)
                .clientIp(extractClientIp(request))
                .payload(payload)
                .build();
    }

    /**
     * Copy the (already masked) request headers, query params and payload onto the
     * current trace span so they show up in Jaeger next to the server span.
     */
    public void tagRequestOnCurrentSpan(RequestLog requestLog) {
        if (requestLog == null) {
            return;
        }
        Span span = Span.current();
        requestLog.headers().forEach((name, value) ->
                span.setAttribute("http.request.header." + name.toLowerCase(Locale.ROOT), LogMaskingUtil.mask(value)));
        if (!requestLog.queryParams().isEmpty()) {
            span.setAttribute("http.request.query", requestLog.queryParams().toString());
        }
        if (requestLog.payload() != null) {
            span.setAttribute("http.request.body", requestLog.payload());
        }
    }

    /**
     * Copy response headers (when header logging is on) and the already masked
     * response payload onto the current trace span.
     */
    public void tagResponseOnCurrentSpan(HttpServletResponse response, String payload) {
        Span span = Span.current();
        if (properties.getRequest().isIncludeHeaders()) { // NOPMD LawOfDemeter
            for (String name : response.getHeaderNames()) {
                String value = LogMaskingUtil.maskHeader(name, new ArrayList<>(response.getHeaders(name)));
                span.setAttribute("http.response.header." + name.toLowerCase(Locale.ROOT), LogMaskingUtil.mask(value));
            }
        }
        if (payload != null) {
            span.setAttribute("http.response.body", payload);
        }
    }

    public ResponseLog buildResponseLog(int status, String statusMessage, long responseTimeMs, long responseSizeBytes, String payload) {
        return ResponseLog.builder()
                .serviceName(serviceName)
                .correlationId(MDCUtil.getCorrelationId())
                .traceId(MDCUtil.get(LoggingConstants.TRACE_ID))
                .spanId(MDCUtil.get(LoggingConstants.SPAN_ID))
                .timestamp(java.time.Instant.now())
                .status(status)
                .statusMessage(statusMessage)
                .responseTimeMs(responseTimeMs)
                .responseSizeBytes(responseSizeBytes)
                .payload(payload)
                .build();
    }

    public void logRequest(Logger logger, RequestLog requestLog) {
        if (requestLog == null || !logger.isInfoEnabled()) {
            return;
        }
        Map<String, String> headers = requestLog.headers();
        if (!headers.isEmpty()) {
            MDCUtil.putAll(headers);
        }
        String query = requestLog.queryParams().isEmpty() ? "" : requestLog.queryParams().toString();
        MDCUtil.put("queryParams", query);
        if (requestLog.payload() != null) {
            MDCUtil.put("requestBody", requestLog.payload());
        }
        try {
            if (requestLog.payload() != null) {
                if (logger.isInfoEnabled()) {
                    logger.info("Incoming request: {} {} {} RequestBody={}",
                    requestLog.method(), requestLog.uri(), query, requestLog.payload());
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("Incoming request: {} {} {}",
                    requestLog.method(), requestLog.uri(), query);
                }
            }
        } finally {
            MDCUtil.remove("queryParams");
            MDCUtil.remove("requestBody");
            for (String key : headers.keySet()) {
                MDCUtil.remove(key);
            }
        }
    }

    public void logResponse(Logger logger, ResponseLog responseLog) {
        if (responseLog == null || !logger.isInfoEnabled()) {
            return;
        }
        MDCUtil.put(LoggingConstants.RESPONSE_STATUS, String.valueOf(responseLog.status()));
        MDCUtil.put("responseCode", String.valueOf(responseLog.status()));
        if (responseLog.statusMessage() != null && !responseLog.statusMessage().isEmpty()) {
            MDCUtil.put("responseMessage", responseLog.statusMessage());
        }
        MDCUtil.put(LoggingConstants.RESPONSE_TIME_MS, String.valueOf(responseLog.responseTimeMs()));
        MDCUtil.put(LoggingConstants.RESPONSE_SIZE_BYTES, String.valueOf(responseLog.responseSizeBytes()));
        if (responseLog.payload() != null) {
            MDCUtil.put("responseBody", responseLog.payload());
        }
        try {
            if (responseLog.payload() != null) {
                if (logger.isInfoEnabled()) {
                    logger.info("Completed response: status={} duration={}ms size={}B ResponseBody={}",
                    responseLog.status(), responseLog.responseTimeMs(),
                    responseLog.responseSizeBytes(), responseLog.payload());
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("Completed response: status={} duration={}ms size={}B",
                    responseLog.status(), responseLog.responseTimeMs(), responseLog.responseSizeBytes());
                }
            }
            logPerformanceIfSlow(logger, responseLog.responseTimeMs(), "request");
        } finally {
            MDCUtil.remove(LoggingConstants.RESPONSE_STATUS);
            MDCUtil.remove("responseCode");
            MDCUtil.remove("responseMessage");
            MDCUtil.remove(LoggingConstants.RESPONSE_TIME_MS);
            MDCUtil.remove(LoggingConstants.RESPONSE_SIZE_BYTES);
            MDCUtil.remove("responseBody");
        }
    }

    private void logPerformanceIfSlow(Logger logger, long durationMs, String context) {
        var threshold = properties.getPerformance().getThreshold();
        if (threshold != null && durationMs > threshold.toMillis()) {
            if (logger.isWarnEnabled()) {
                logger.warn("Slow {} detected: {}ms (threshold {}ms)", context, durationMs, threshold.toMillis());
            }
        }
    }

    /**
     * Return {@code true} when the given path matches any of the configured
     * excluded patterns.
     */
    public boolean isExcludedPath(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String path = request.getServletPath();
        if (!StringUtils.hasText(path)) {
            path = request.getRequestURI();
        }
        for (String pattern : properties.getExcludedPaths()) {
            if (AntPathMatcherUtil.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    public void clearMdc() {
        MDCUtil.clear();
    }
}
