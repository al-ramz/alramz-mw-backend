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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
