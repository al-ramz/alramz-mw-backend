package com.alramz.logging;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.model.RequestLog;
import com.alramz.logging.model.ResponseLog;
import com.alramz.logging.util.LoggingHelper;
import com.alramz.logging.util.LogMaskingUtil;
import com.alramz.logging.util.MDCUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import com.alramz.logging.otel.TraceContextExtractor;
import org.slf4j.Logger;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoggingHelperTest {

    private LoggingProperties properties;
    private LoggingHelper helper;

    @BeforeEach
    void setUp() {
        LogMaskingUtil.configure(true, "********", List.of("password", "token"), List.of());
        properties = new LoggingProperties();
        properties.setRequest(new LoggingProperties.RequestProperties());
        properties.setResponse(new LoggingProperties.ResponseProperties());
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "test-service");
        helper = new LoggingHelper(env, properties, List.of());
    }

    @AfterEach
    void tearDown() {
        helper.clearMdc();
    }

    @Test
    void serviceNameIsReadFromEnvironment() {
        assertThat(helper.getServiceName()).isEqualTo("test-service");
        assertThat(helper.getCorrelationIdHeader()).isEqualTo("X-Correlation-ID");
    }

    @Test
    void buildRequestLogCapturesHttpMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setQueryString("active=true");
        request.setParameter("active", "true");
        request.setRemoteAddr("203.0.113.7");

        RequestLog log = helper.buildRequestLog(request, "corr-123");

        assertThat(log.method()).isEqualTo("GET");
        assertThat(log.uri()).isEqualTo("/api/users");
        assertThat(log.queryParams()).containsEntry("active", "true");
        assertThat(log.correlationId()).isEqualTo("corr-123");
        assertThat(log.serviceName()).isEqualTo("test-service");
        assertThat(log.clientIp()).isEqualTo("203.0.113.7");
    }

    @Test
    void buildRequestLogReturnsNullForNullRequest() {
        assertThat(helper.buildRequestLog(null, "corr-1")).isNull();
    }

    @Test
    void buildResponseLogPopulatesFields() {
        ResponseLog log = helper.buildResponseLog(200, "OK", 53, 128, null);
        assertThat(log.status()).isEqualTo(200);
        assertThat(log.statusMessage()).isEqualTo("OK");
        assertThat(log.responseTimeMs()).isEqualTo(53);
        assertThat(log.responseSizeBytes()).isEqualTo(128);
        assertThat(log.correlationId()).isNull();
        assertThat(log.serviceName()).isEqualTo("test-service");
    }

    @Test
    void isExcludedPathMatchesAntPatterns() {
        MockHttpServletRequest actuator = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletRequest api = new MockHttpServletRequest("GET", "/api/users");
        assertThat(helper.isExcludedPath(actuator)).isTrue();
        assertThat(helper.isExcludedPath(api)).isFalse();
    }

    @Test
    void populateMdcSetsDiagnosticContext() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
        request.setQueryString("active=true");
        request.setRemoteAddr("203.0.113.7");

        helper.populateMdc(request);

        assertThat(MDCUtil.get("uri")).isEqualTo("/api/users");
        assertThat(MDCUtil.get("queryString")).isEqualTo("active=true");
        assertThat(MDCUtil.get("clientIp")).isEqualTo("203.0.113.7");
        helper.clearMdc();
        assertThat(MDCUtil.get("correlationId")).isNull();
    }

    private static ContentCachingRequestWrapper cached(String body) throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/api/users");
        raw.setContent(body.getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(raw, 10_000);
        wrapper.getInputStream().readAllBytes();
        return wrapper;
    }

    @Test
    void extractClientIpPrefersForwardingHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        assertThat(LoggingHelper.extractClientIp(request)).isEqualTo("10.0.0.1");

        request.addHeader("X-Real-IP", "unknown");
        request.addHeader("Proxy-Client-IP", "198.51.100.2");
        assertThat(LoggingHelper.extractClientIp(request)).isEqualTo("198.51.100.2");

        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.2");
        assertThat(LoggingHelper.extractClientIp(request)).isEqualTo("203.0.113.1");
        assertThat(LoggingHelper.extractClientIp(null)).isNull();
    }

    @Test
    void queryParamsAndHeadersAreMasked() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("q", "{\"password\":\"p\"}");
        request.addParameter("empty", new String[0]);
        request.addHeader("Authorization-Token", "abc");
        request.addHeader("Accept", "json");

        assertThat(helper.toQueryParams(request)).containsEntry("q", "{\"password\": \"********\"}")
                .doesNotContainKey("empty");
        assertThat(helper.toHeaders(request)).containsEntry("Authorization-Token", "********")
                .containsEntry("Accept", "json");
        assertThat(helper.toQueryParams(null)).isEmpty();
        assertThat(helper.toHeaders(null)).isEmpty();
    }

    @Test
    void extractPayloadMasksAndTruncatesCachedBody() throws Exception {
        properties.getRequest().setMaxPayloadLength(20);

        assertThat(helper.extractPayload(cached("{\"token\":\"t\"}"))).isEqualTo("{\"token\": \"********\"}");
        assertThat(helper.extractPayload(cached("x".repeat(30)))).isEqualTo("x".repeat(20) + "...[truncated]");
        assertThat(helper.extractPayload(cached(""))).isNull();
        assertThat(helper.extractPayload(new MockHttpServletRequest())).isNull();
        assertThat(helper.extractPayload(null)).isNull();
    }

    @Test
    void buildRequestLogIncludesHeadersAndPayloadWhenEnabled() throws Exception {
        properties.getRequest().setIncludeHeaders(true);
        properties.getRequest().setIncludePayload(true);
        ContentCachingRequestWrapper request = cached("{\"name\":\"a\"}");
        ((MockHttpServletRequest) request.getRequest()).addHeader("X-Trace", "1");

        RequestLog log = helper.buildRequestLog(request, "c-1");

        assertThat(log.headers()).containsEntry("X-Trace", "1");
        assertThat(log.payload()).isEqualTo("{\"name\": \"a\"}");
        assertThatCode(() -> helper.tagRequestOnCurrentSpan(log)).doesNotThrowAnyException();
        assertThatCode(() -> helper.tagRequestOnCurrentSpan(null)).doesNotThrowAnyException();
    }

    @Test
    void tagResponseReadsHeadersOnlyWhenHeaderLoggingEnabled() {
        properties.getRequest().setIncludeHeaders(true);
        MockHttpServletResponse response = mock(MockHttpServletResponse.class);
        when(response.getHeaderNames()).thenReturn(List.of("Set-Cookie"));
        when(response.getHeaders("Set-Cookie")).thenReturn(List.of("a=b"));

        helper.tagResponseOnCurrentSpan(response, "{}");
        verify(response).getHeaders("Set-Cookie");

        properties.getRequest().setIncludeHeaders(false);
        MockHttpServletResponse quiet = mock(MockHttpServletResponse.class);
        helper.tagResponseOnCurrentSpan(quiet, null);
        verify(quiet, never()).getHeaderNames();
    }

    @Test
    void logRequestExposesContextInMdcOnlyDuringLogCall() {
        Logger logger = mock(Logger.class);
        when(logger.isInfoEnabled()).thenReturn(true);
        Map<String, String> seen = new HashMap<>();
        doAnswer(inv -> {
            seen.put("requestBody", MDCUtil.get("requestBody"));
            seen.put("queryParams", MDCUtil.get("queryParams"));
            seen.put("X-H", MDCUtil.get("X-H"));
            return null;
        }).when(logger).info(anyString(), any(Object[].class));
        RequestLog withBody = RequestLog.builder().method("POST").uri("/x").queryParams(Map.of("a", "1"))
                .headers(Map.of("X-H", "v")).payload("{}").build();

        helper.logRequest(logger, withBody);

        assertThat(seen).containsEntry("requestBody", "{}").containsEntry("queryParams", "{a=1}")
                .containsEntry("X-H", "v");
        assertThat(MDCUtil.get("requestBody")).isNull();
        assertThat(MDCUtil.get("X-H")).isNull();

        RequestLog noBody = RequestLog.builder().method("GET").uri("/y").payload(null).build();
        helper.logRequest(logger, noBody);
        verify(logger).info(eq("Incoming request: {} {} {}"), eq("GET"), eq("/y"), eq(""));
        helper.logRequest(logger, null);
    }

    @Test
    void logResponseSetsStatusMdcAndWarnsWhenSlow() {
        properties.getPerformance().setThreshold(Duration.ofMillis(10));
        Logger logger = mock(Logger.class);
        when(logger.isInfoEnabled()).thenReturn(true);
        when(logger.isWarnEnabled()).thenReturn(true);
        Map<String, String> seen = new HashMap<>();
        doAnswer(inv -> {
            seen.put("responseCode", MDCUtil.get("responseCode"));
            seen.put("responseMessage", MDCUtil.get("responseMessage"));
            seen.put("responseBody", MDCUtil.get("responseBody"));
            return null;
        }).when(logger).info(anyString(), any(Object[].class));

        helper.logResponse(logger, helper.buildResponseLog(404, "Not Found", 50, 2, "{}"));

        assertThat(seen).containsEntry("responseCode", "404").containsEntry("responseMessage", "Not Found")
                .containsEntry("responseBody", "{}");
        verify(logger).warn(eq("Slow {} detected: {}ms (threshold {}ms)"), eq("request"), eq(50L), eq(10L));
        assertThat(MDCUtil.get("responseCode")).isNull();

        helper.logResponse(logger, helper.buildResponseLog(200, "", 1, 0, null));
        verify(logger).info(eq("Completed response: status={} duration={}ms size={}B"), eq(200), eq(1L), eq(0L));

        Logger silent = mock(Logger.class);
        helper.logResponse(silent, helper.buildResponseLog(200, "OK", 1, 0, null));
        helper.logRequest(silent, RequestLog.builder().method("GET").uri("/").build());
        verify(silent, never()).info(anyString(), any(Object[].class));
    }

    @Test
    void populateMdcMasksSensitiveIdentityHeadersAndUsesSupportedExtractor() {
        properties.getCorrelationId().setUserIdHeader("X-User-Token");
        TraceContextExtractor otel = mock(TraceContextExtractor.class);
        when(otel.isSupported()).thenReturn(true);
        when(otel.traceId()).thenReturn("trace-1");
        when(otel.spanId()).thenReturn("span-1");
        TraceContextExtractor unsupported = mock(TraceContextExtractor.class);
        LoggingHelper withOtel = new LoggingHelper(new MockEnvironment(), properties, List.of(unsupported, otel));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.addHeader("X-User-Token", "u-1");
        request.addHeader("X-Tenant-ID", "t-1");

        withOtel.populateMdc(request);

        assertThat(withOtel.getServiceName()).isEqualTo("unknown");
        assertThat(withOtel.getTraceContextExtractor()).isSameAs(otel);
        assertThat(withOtel.isMaskingEnabled()).isTrue();
        assertThat(MDCUtil.get("userId")).isEqualTo("********");
        assertThat(MDCUtil.get("tenantId")).isEqualTo("t-1");
        assertThat(MDCUtil.get("traceId")).isEqualTo("trace-1");
        assertThat(MDCUtil.get("spanId")).isEqualTo("span-1");
        assertThat(MDCUtil.get("queryString")).isNull();
    }

    @Test
    void isExcludedPathUsesServletPathAndHandlesNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ctx/swagger-ui/index.html");
        request.setServletPath("/swagger-ui/index.html");

        assertThat(helper.isExcludedPath(request)).isTrue();
        assertThat(helper.isExcludedPath(null)).isFalse();
        assertThat(new LoggingHelper(new MockEnvironment(), properties, null).getTraceContextExtractor()
                .isSupported()).isFalse();
    }
}
