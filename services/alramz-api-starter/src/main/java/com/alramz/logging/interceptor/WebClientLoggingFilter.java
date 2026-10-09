package com.alramz.logging.interceptor;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.LogMaskingUtil;
import com.alramz.logging.util.MDCUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive counterpart of {@link RestTemplateLoggingInterceptor}. Produces an
 * {@link ExchangeFilterFunction} that logs outgoing
 * {@link org.springframework.web.reactive.function.client.WebClient} calls,
 * propagates the correlation id and enforces the performance threshold.
 */
public class WebClientLoggingFilter {

    private static final Logger logger = LoggerFactory.getLogger(WebClientLoggingFilter.class);

    private final LoggingProperties properties;

    public WebClientLoggingFilter(LoggingProperties properties) {
        this.properties = properties;
    }

    public ExchangeFilterFunction filterFunction() {
        return (clientRequest, next) -> {
            String headerName = properties.getCorrelationId().getHeader(); // NOPMD LawOfDemeter
            String correlationId = MDCUtil.getCorrelationId();
            ClientRequest effectiveRequest = correlationId != null
                    && !clientRequest.headers().containsHeader(headerName)
                    ? ClientRequest.from(clientRequest)
                        .headers(headers -> headers.set(headerName, correlationId))
                        .build()
                    : clientRequest;

            AtomicReference<String> sentBody = new AtomicReference<>();
            ClientRequest sentRequest = properties.getRequest().isIncludePayload() // NOPMD LawOfDemeter
                    ? WebClientBodies.captureRequestBody(effectiveRequest, sentBody::set)
                    : effectiveRequest;

            long start = System.nanoTime();
            if (logger.isInfoEnabled()) {
                if (logger.isInfoEnabled()) {
                    logger.info("Outgoing {} {} (correlationId={})",
                    effectiveRequest.method(), effectiveRequest.url(), correlationId);
                }
            }
            // The WebClient client observation travels in the Reactor context; its key/values
            // become span tags when the observation stops (after this filter's Mono completes).
            return Mono.deferContextual(ctx -> next.exchange(sentRequest)
                    .flatMap(response -> tagSpan(ctx.getOrDefault(ObservationThreadLocalAccessor.KEY, null),
                            sentRequest, sentBody.get(), response)))
                    .doOnSuccess(response -> {
                        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                        int status = response.statusCode().value();
                        if (logger.isInfoEnabled()) {
                            logger.info("Outgoing response {} {} in {}ms status={}",
                            effectiveRequest.method(), effectiveRequest.url(), elapsedMs, status);
                        }
                        warnIfSlow(elapsedMs, "outgoing call");
                    })
                    .doOnError(error -> {
                        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                        if (logger.isErrorEnabled()) {
                            if (logger.isErrorEnabled()) {
                                logger.error("Outgoing {} {} failed in {}ms (correlationId={})",
                                effectiveRequest.method(), effectiveRequest.url(), elapsedMs, correlationId, error);
                            }
                        }
                    });
        };
    }

    private Mono<ClientResponse> tagSpan(Observation observation, ClientRequest request, String requestBody,
                                         ClientResponse response) {
        if (observation == null) {
            return Mono.just(response);
        }
        if (properties.getRequest().isIncludeHeaders()) { // NOPMD LawOfDemeter
            tagHeaders(observation, "http.request.header.", request.headers());
            tagHeaders(observation, "http.response.header.", response.headers().asHttpHeaders());
        }
        if (requestBody != null && !requestBody.isEmpty()) {
            observation.highCardinalityKeyValue("http.request.body",
                    maskAndTruncate(requestBody, properties.getRequest().getMaxPayloadLength())); // NOPMD LawOfDemeter
        }
        if (!properties.getResponse().isIncludePayload() // NOPMD LawOfDemeter
                || !WebClientBodies.isTextual(response.headers().contentType().orElse(null))) {
            return Mono.just(response);
        }
        return WebClientBodies.readBody(response, body -> {
            if (!body.isEmpty()) {
                observation.highCardinalityKeyValue("http.response.body",
                        maskAndTruncate(body, properties.getResponse().getMaxPayloadLength())); // NOPMD LawOfDemeter
            }
        });
    }

    private static void tagHeaders(Observation observation, String prefix, HttpHeaders headers) {
        headers.forEach((name, values) -> observation.highCardinalityKeyValue(
                prefix + name.toLowerCase(Locale.ROOT), LogMaskingUtil.mask(LogMaskingUtil.maskHeader(name, values))));
    }

    private static String maskAndTruncate(String value, int maxLength) {
        String limited = value.length() > maxLength ? value.substring(0, maxLength) + "...[truncated]" : value;
        return LogMaskingUtil.mask(limited);
    }

    public static ExchangeFilterFunction of(LoggingProperties properties) {
        return new WebClientLoggingFilter(properties).filterFunction();
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
