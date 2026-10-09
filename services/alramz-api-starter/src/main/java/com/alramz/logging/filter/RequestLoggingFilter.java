package com.alramz.logging.filter;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.constants.LoggingConstants;
import com.alramz.logging.model.RequestLog;
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
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * Logs incoming HTTP requests (method, URI, query parameters, selected
 * headers, client ip and optional payload) after the request has been handled,
 * so that the cached request body is available when payload logging is enabled.
 * <p>
 * Disabled paths (actuator, swagger, ...) are skipped to keep noise low.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final LoggingProperties properties;
    private final LoggingHelper loggingHelper;

    public RequestLoggingFilter(LoggingProperties properties, LoggingHelper loggingHelper) {
        this.properties = properties;
        this.loggingHelper = loggingHelper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper wrapped =
                new ContentCachingRequestWrapper(request, properties.getRequest().getMaxPayloadLength()); // NOPMD LawOfDemeter
        try {
            filterChain.doFilter(wrapped, response);
        } catch (Exception e) {
            logger.error("Exception during request processing for {} {}", request.getMethod(), request.getRequestURI(), e);
            if (e instanceof IOException ioe) {
                throw ioe;
            } else if (e instanceof ServletException se) {
                throw se;
            } else {
                throw new ServletException(e);
            }
        } finally {
            String correlationId = (String) request.getAttribute(LoggingConstants.CORRELATION_ID_ATTRIBUTE);
            RequestLog requestLog = loggingHelper.buildRequestLog(wrapped, correlationId);
            loggingHelper.logRequest(logger, requestLog);
            loggingHelper.tagRequestOnCurrentSpan(requestLog);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getRequest().isEnabled() || loggingHelper.isExcludedPath(request); // NOPMD LawOfDemeter
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }
}
