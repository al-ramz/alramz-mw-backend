package com.alramz.logging.interceptor;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.LogMaskingUtil;
import com.alramz.logging.util.MDCUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Duration;

/**
 * {@link ClientHttpRequestInterceptor} that transparently logs outgoing
 * {@link org.springframework.web.client.RestTemplate} calls and propagates the
 * correlation id to downstream services.
 */
public class RestTemplateLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RestTemplateLoggingInterceptor.class);

    private final LoggingProperties properties;

    public RestTemplateLoggingInterceptor(LoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String headerName = properties.getCorrelationId().getHeader();
        String correlationId = MDCUtil.getCorrelationId();
        if (correlationId != null && !request.getHeaders().containsHeader(headerName)) {
            request.getHeaders().set(headerName, correlationId);
        }

        long start = System.nanoTime();
        if (logger.isInfoEnabled()) {
            String bodyText = shouldLogPayload() ? truncate(masking(body)) : null;
            logger.info("Outgoing {} {} (correlationId={}{})",
                    request.getMethod(), request.getURI(), correlationId,
                    bodyText == null ? "" : " body=" + bodyText);
        }
        try {
            ClientHttpResponse response = execution.execute(request, body);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            int status = response.getStatusCode().value();
            if (logger.isInfoEnabled()) {
                logger.info("Outgoing response {} {} in {}ms status={}",
                        request.getMethod(), request.getURI(), elapsedMs, status);
            }
            warnIfSlow(elapsedMs, "outgoing call");
            return response;
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (logger.isErrorEnabled()) {
                logger.error("Outgoing {} {} failed in {}ms (correlationId={})",
                        request.getMethod(), request.getURI(), elapsedMs, correlationId, ex);
            }
            throw ex;
        }
    }

    private boolean shouldLogPayload() {
        return properties.getRequest().isIncludePayload(); // NOPMD LawOfDemeter
    }

    private String masking(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        return new String(payload, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        int max = properties.getRequest().getMaxPayloadLength(); // NOPMD LawOfDemeter
        value = LogMaskingUtil.mask(value);
        return value.length() <= max ? value : value.substring(0, max) + "...[truncated]";
    }

    private void warnIfSlow(long durationMs, String context) {
        Duration threshold = properties.getPerformance().getThreshold();
        if (threshold != null && durationMs > threshold.toMillis()) {
            if (logger.isWarnEnabled()) {
                logger.warn("Slow {} detected: {}ms (threshold {}ms)", context, durationMs, threshold.toMillis());
            }
        }
    }
}
