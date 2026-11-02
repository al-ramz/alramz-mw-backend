package com.alramz.logging.filter;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.constants.LoggingConstants;
import com.alramz.logging.model.RequestLog;
import com.alramz.logging.util.LoggingHelper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLoggingFilterTest {

    private final LoggingProperties properties = new LoggingProperties();
    private final LoggingHelper helper = mock(LoggingHelper.class);
    private final RequestLoggingFilter filter = new RequestLoggingFilter(properties, helper);

    @Test
    void wrapsRequestAndLogsAfterChainWithCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/x");
        request.setAttribute(LoggingConstants.CORRELATION_ID_ATTRIBUTE, "corr-9");
        RequestLog log = RequestLog.builder().build();
        when(helper.buildRequestLog(any(), eq("corr-9"))).thenReturn(log);
        Object[] seen = new Object[1];

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen[0] = req);

        assertThat(seen[0]).isInstanceOf(ContentCachingRequestWrapper.class);
        verify(helper).logRequest(any(Logger.class), eq(log));
        verify(helper).tagRequestOnCurrentSpan(log);
    }

    @Test
    void exceptionsAreRethrownOrWrappedAndRequestStillLogged() {
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/a"), new MockHttpServletResponse(),
                (req, res) -> {
                    throw new IOException("io");
                })).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/b"), new MockHttpServletResponse(),
                (req, res) -> {
                    throw new ServletException("se");
                })).isInstanceOf(ServletException.class).hasMessage("se");
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/c"), new MockHttpServletResponse(),
                (req, res) -> {
                    throw new IllegalStateException("x");
                })).isInstanceOf(ServletException.class).hasCauseInstanceOf(IllegalStateException.class);

        verify(helper, org.mockito.Mockito.times(3)).logRequest(any(Logger.class), any());
    }

    @Test
    void skipsExcludedPathsAndWhenDisabled() throws Exception {
        MockHttpServletRequest excluded = new MockHttpServletRequest("GET", "/actuator/health");
        when(helper.isExcludedPath(excluded)).thenReturn(true);
        filter.doFilter(excluded, new MockHttpServletResponse(), (req, res) -> { });

        properties.getRequest().setEnabled(false);
        filter.doFilter(new MockHttpServletRequest("GET", "/api"), new MockHttpServletResponse(), (req, res) -> { });

        verify(helper, never()).logRequest(any(), any());
    }
}
