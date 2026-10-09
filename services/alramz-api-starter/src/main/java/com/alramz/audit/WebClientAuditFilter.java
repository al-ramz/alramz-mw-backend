package com.alramz.audit;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.interceptor.WebClientBodies;
import com.alramz.logging.util.MDCUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes one {@code OUTBOUND} {@code api_audit_log} row for every WebClient exchange, from what was actually
 * sent and received: URL, method, status, masked bodies and duration. A service opts in with
 * {@code company.logging.database-logging.outbound-enabled=true}; the filter is then composed into the shared
 * {@code alramzWebClientLoggingFilterFunction}, so every WebClient built with that filter is audited.
 */
@Slf4j
public class WebClientAuditFilter implements ExchangeFilterFunction {

    static final String CANCELLED = "Cancelled before a response arrived (timeout or caller cancelled)";

    private final ApiAuditLogService auditLogService;
    private final LoggingProperties.DatabaseLoggingProperties settings;
    private final String serviceName;
    private final Scheduler writeScheduler;

    /**
     * @param writeScheduler where the blocking database insert runs; never the HTTP client's event loop
     */
    public WebClientAuditFilter(ApiAuditLogService auditLogService, LoggingProperties properties,
                                String serviceName, Scheduler writeScheduler) {
        this.auditLogService = auditLogService;
        this.settings = properties.getDatabaseLogging();
        this.serviceName = serviceName;
        this.writeScheduler = writeScheduler;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        Exchange exchange = new Exchange(request, MDCUtil.getCorrelationIdAsUuid(), System.nanoTime());
        ClientRequest sent = settings.isIncludeRequestPayload()
                ? WebClientBodies.captureRequestBody(request, exchange.requestBody::set)
                : request;
        return next.exchange(sent)
                .flatMap(response -> recordResponse(exchange, response))
                .doOnError(error -> exchange.record(null, null, error.getMessage(), error.getClass().getName()))
                .doOnCancel(() -> exchange.record(null, null, CANCELLED, null));
    }

    private Mono<ClientResponse> recordResponse(Exchange exchange, ClientResponse response) {
        HttpStatusCode status = response.statusCode();
        if (!settings.isIncludeResponsePayload()
                || !WebClientBodies.isTextual(response.headers().contentType().orElse(null))) {
            exchange.record(status, null, null, null);
            return Mono.just(response);
        }
        return WebClientBodies.readBody(response, body -> exchange.record(status, body, null, null));
    }

    /** One outgoing call; records itself exactly once, whichever of response, error or cancel arrives first. */
    private final class Exchange {

        private final ClientRequest request;
        private final UUID correlationId;
        private final long startNanos;
        private final AtomicReference<String> requestBody = new AtomicReference<>();
        private final AtomicBoolean recorded = new AtomicBoolean();

        private Exchange(ClientRequest request, UUID correlationId, long startNanos) {
            this.request = request;
            this.correlationId = correlationId;
            this.startNanos = startNanos;
        }

        /** {@code status} is null when no response arrived; then {@code cause} says why. */
        void record(HttpStatusCode status, String responseBody, String cause, String causeClass) {
            if (!recorded.compareAndSet(false, true)) {
                return;
            }
            boolean failed = status == null || status.isError();
            SensitiveDataMasker masker = auditLogService.getMasker();
            ApiAuditLog entry = new ApiAuditLog(
                    correlationId,
                    "OUTBOUND",
                    serviceName,
                    request.url().getHost(),
                    // Query strings can carry credentials, so only scheme, host and path are kept.
                    UriComponentsBuilder.fromUri(request.url()).replaceQuery(null).fragment(null).toUriString(),
                    request.method().name(),
                    masker.maskBody(requestBody.get()),
                    masker.maskBody(responseBody),
                    failed ? "FAILURE" : "SUCCESS",
                    status != null ? status.value() : null,
                    status != null && status.isError() ? status.toString() : cause,
                    causeClass,
                    (System.nanoTime() - startNanos) / 1_000_000L,
                    Instant.now());
            try {
                writeScheduler.schedule(() -> auditLogService.log(entry));
            } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException - auditing must never fail the call
                log.warn("Dropped outbound audit record for {} {}", entry.method(), entry.apiEndpoint(), e);
            }
        }
    }
}
