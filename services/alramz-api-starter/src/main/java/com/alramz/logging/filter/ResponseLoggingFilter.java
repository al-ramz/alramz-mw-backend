package com.alramz.logging.filter;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.constants.LoggingConstants;
import com.alramz.logging.model.ResponseLog;
import com.alramz.logging.util.LoggingHelper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/**
 * Logs outgoing HTTP responses (status, duration, response size and optional
 * payload) and guarantees the cached response body is flushed to the client.
 * <p>
 * Ordered outside {@link RequestLoggingFilter} so that the request log line is
 * emitted before the response log line, giving a natural request-then-response
 * order in the logs.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseLoggingFilter.class);

    private final LoggingProperties properties;
    private final LoggingHelper loggingHelper;

    public ResponseLoggingFilter(LoggingProperties properties, LoggingHelper loggingHelper) {
        this.properties = properties;
        this.loggingHelper = loggingHelper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, cachingResponse);
        } catch (Exception e) {
            logger.error("Exception during response processing for {} {}", request.getMethod(), request.getRequestURI(), e);
            if (e instanceof IOException ioe) {
                throw ioe;
            } else if (e instanceof ServletException se) {
                throw se;
            } else {
                throw new ServletException(e);
            }
        } finally {
            long startNanos = getStartNanos(request);
            long durationMs = startNanos == 0L ? 0L : (System.nanoTime() - startNanos) / 1_000_000L;
            byte[] body = cachingResponse.getContentAsByteArray();
            long size = body.length;
            int status = cachingResponse.getStatus();
            String payload = null;
            if (properties.getResponse().isIncludePayload() && size > 0) { // NOPMD LawOfDemeter
                payload = truncate(new String(body, StandardCharsets.UTF_8), properties.getResponse().getMaxPayloadLength()); // NOPMD LawOfDemeter
                if (loggingHelper.isMaskingEnabled()) {
                    payload = com.alramz.logging.util.LogMaskingUtil.mask(payload);
                }
            }
            ResponseLog responseLog = loggingHelper.buildResponseLog(
                    status,
                    org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase(),
                    durationMs,
                    size,
                    payload);
            loggingHelper.logResponse(logger, responseLog);
            loggingHelper.tagResponseOnCurrentSpan(cachingResponse, payload);
            cachingResponse.copyBodyToResponse();
        }
    }

    private long getStartNanos(HttpServletRequest request) {
        Object value = request.getAttribute(LoggingConstants.START_TIME_ATTRIBUTE);
        return value instanceof Long ? (Long) value : 0L;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[truncated]";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getResponse().isEnabled() || loggingHelper.isExcludedPath(request); // NOPMD LawOfDemeter
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }
}
