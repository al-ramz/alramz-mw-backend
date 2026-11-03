package com.alramz.logging.filter;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.constants.LoggingConstants;
import com.alramz.logging.model.ResponseLog;
import com.alramz.logging.util.LogMaskingUtil;
import com.alramz.logging.util.LoggingHelper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResponseLoggingFilterTest {

    private LoggingProperties properties;
    private LoggingHelper helper;
    private ResponseLoggingFilter filter;

    @BeforeEach
    void setUp() {
        LogMaskingUtil.configure(true, "********", List.of("password"), List.of());
        properties = new LoggingProperties();
        helper = mock(LoggingHelper.class);
        filter = new ResponseLoggingFilter(properties, helper);
    }

    @Test
    void logsMaskedTruncatedPayloadAndCopiesBodyToClient() throws Exception {
        properties.getResponse().setIncludePayload(true);
        properties.getResponse().setMaxPayloadLength(25);
        when(helper.isMaskingEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.setAttribute(LoggingConstants.START_TIME_ATTRIBUTE, System.nanoTime());
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = "{\"password\":\"p\",\"other\":\"value-that-is-long\"}";

        filter.doFilter(request, response, (req, res) -> {
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
            res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        });

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(helper).buildResponseLog(eq(201), eq("Created"), anyLong(), eq((long) body.length()), payload.capture());
        assertThat(payload.getValue()).endsWith("...[truncated]").doesNotContain("\"p\"");
        verify(helper).logResponse(any(Logger.class), any());
        assertThat(response.getContentAsString()).isEqualTo(body);
    }

    @Test
    void payloadOmittedWhenDisabledAndExceptionsAreWrapped() {
        ResponseLog log = mock(ResponseLog.class);
        when(helper.buildResponseLog(eq(200), anyString(), anyLong(), anyLong(), isNull())).thenReturn(log);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(ServletException.class).hasCauseInstanceOf(IllegalStateException.class);

        verify(helper).logResponse(any(Logger.class), eq(log));
    }

    @Test
    void ioAndServletExceptionsAreRethrownAsIs() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new IOException("io");
        })).isInstanceOf(IOException.class).hasMessage("io");

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/y"),
                new MockHttpServletResponse(), (req, res) -> {
                    throw new ServletException("se");
                })).isInstanceOf(ServletException.class).hasMessage("se");
    }

    @Test
    void skipsExcludedPathsAndDisabledResponseLogging() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        when(helper.isExcludedPath(request)).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        properties.getResponse().setEnabled(false);
        filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), new MockHttpServletResponse(), (req, res) -> { });

        verify(helper, never()).logResponse(any(), any());
    }
}
