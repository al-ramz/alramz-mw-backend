package com.alramz.logging.filter;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.constants.LoggingConstants;
import com.alramz.logging.util.LoggingHelper;
import com.alramz.logging.util.MDCUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that ensures every request carries a correlation id.
 * <p>
 * Order and responsibilities:
 * <ul>
 *   <li>Reads the {@code X-Correlation-ID} header (configurable) if present.</li>
 *   <li>Generates a random UUID when the header is absent.</li>
 *   <li>Stores the correlation id in the {@link MDC} and the request as an
 *   attribute for downstream filters.</li>
 *   <li>Populates the full diagnostic context (service, client ip, user,
 *   tenant, trace/span ids, ...).</li>
 *   <li>Returns the correlation id to the client through the response header.</li>
 *   <li>Clears the MDC once the request completes.</li>
 * </ul>
 * <p>
 * Registered with the highest precedence so that every subsequent log line
 * automatically includes the correlation id.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);

    private final LoggingProperties properties;
    private final LoggingHelper loggingHelper;

    public CorrelationIdFilter(LoggingProperties properties, LoggingHelper loggingHelper) {
        this.properties = properties;
        this.loggingHelper = loggingHelper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = extractCorrelationId(request);
        MDCUtil.putCorrelationId(correlationId);
        request.setAttribute(LoggingConstants.CORRELATION_ID_ATTRIBUTE, correlationId);
        request.setAttribute(LoggingConstants.START_TIME_ATTRIBUTE, System.nanoTime());

        loggingHelper.populateMdc(request);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (properties.getCorrelationId().isResponseHeader()) { // NOPMD LawOfDemeter
                response.setHeader(properties.getCorrelationId().getHeader(), correlationId); // NOPMD LawOfDemeter
            }
            MDCUtil.clear();
        }
    }

    private String extractCorrelationId(HttpServletRequest request) {
        String header = properties.getCorrelationId().getHeader(); // NOPMD LawOfDemeter
        String correlationId = request.getHeader(header);
        if (StringUtils.hasText(correlationId)) {
            return correlationId.trim();
        }
        return UUID.randomUUID().toString();
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }
}
