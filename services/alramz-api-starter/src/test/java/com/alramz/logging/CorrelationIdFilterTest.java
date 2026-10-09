package com.alramz.logging;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.filter.CorrelationIdFilter;
import com.alramz.logging.util.LoggingHelper;
import com.alramz.logging.util.MDCUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private LoggingProperties properties;
    private LoggingHelper helper;
    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        properties = new LoggingProperties();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "test-service");
        helper = new LoggingHelper(env, properties, List.of());
        filter = new CorrelationIdFilter(properties, helper);
        MDCUtil.clear();
    }

    @AfterEach
    void tearDown() {
        MDCUtil.clear();
    }

    @Test
    void generatesCorrelationIdWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] captured = new String[1];

        filter.doFilter(request, response, (req, resp) -> captured[0] = MDCUtil.getCorrelationId());

        String header = response.getHeader("X-Correlation-ID");
        assertThat(header).isNotNull().isNotBlank();
        assertThat(captured[0]).isEqualTo(header);
        assertThat(request.getAttribute("alramz.logging.correlationId")).isEqualTo(header);
        // After the filter returns the MDC is cleared.
        assertThat(MDCUtil.get("correlationId")).isNull();
    }

    @Test
    void propagatesExistingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader("X-Correlation-ID", "fixed-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> {});

        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("fixed-123");
        assertThat(request.getAttribute("alramz.logging.correlationId")).isEqualTo("fixed-123");
    }

    @Test
    void doesNotEchoHeaderWhenDisabled() throws Exception {
        properties.getCorrelationId().setResponseHeader(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> {});

        assertThat(response.getHeader("X-Correlation-ID")).isNull();
    }
}
