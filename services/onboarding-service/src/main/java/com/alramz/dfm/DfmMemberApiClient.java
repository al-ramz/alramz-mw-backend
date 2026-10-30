package com.alramz.dfm;

import com.alramz.config.DfmMemberApiProperties;
import com.alramz.exception.DfmConfigurationException;
import com.alramz.model.DfmInvestorRegistrationRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Calls DFM's Member API {@code POST /memberapis/onboarding} and classifies every outcome.
 * <p>
 * Deliberately not under {@code com.alramz.client} (the starter's ApiAuditAspect would persist the unmasked payload),
 * not {@code @Loggable}, and not an AbstractRestClient (which would forward the caller's JWT to DFM). Logs only the
 * HTTP status, DFM's response code/message, the request id and the duration.
 */
@Slf4j
@Component
public class DfmMemberApiClient {

    private static final String SECURITY_KEY_HEADER = "securitykey";

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final DfmMemberApiProperties properties;
    private final ObjectMapper objectMapper;

    public DfmMemberApiClient(
            @Qualifier("dfmMemberApiWebClient") WebClient webClient,
            @Qualifier("dfmMemberApiCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("dfmMemberApiRetry") Retry retry,
            @Qualifier("dfmMemberApiTimeLimiter") TimeLimiter timeLimiter,
            DfmMemberApiProperties properties,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.timeLimiter = timeLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public DfmOnboardingOutcome onboard(DfmInvestorRegistrationRequest request) {
        requireConfigured();
        String requestId = String.valueOf(request.getRequestId());
        String json = serialize(DfmOnboardingPayload.from(request, properties.memberCode(), properties.bodySecurityKey()));
        long start = System.nanoTime();

        DfmOnboardingOutcome outcome = webClient.post()
                .uri(properties.baseUrl() + properties.onboardingPath())
                .header(SECURITY_KEY_HEADER, properties.headerSecurityKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> classify(response.statusCode(), body, requestId)))
                // Breaker outermost: one caller request is one breaker call, including the connect retry.
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(error -> Mono.just(unavailable(error)))
                .block();

        if (outcome instanceof DfmOnboardingOutcome.Unavailable unavailable) {
            log.warn("DFM onboarding unavailable: reason={} request_id={} duration_ms={}",
                    unavailable.reason(), requestId, (System.nanoTime() - start) / 1_000_000);
        } else {
            log.info("DFM onboarding finished: outcome={} request_id={} duration_ms={}",
                    outcome == null ? null : outcome.getClass().getSimpleName(), requestId, (System.nanoTime() - start) / 1_000_000);
        }
        return outcome;
    }

    /** True when DFM was never reached (connection refused or connect timeout), so resending is safe. */
    public static boolean isConnectFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConnectException || t instanceof HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private void requireConfigured() {
        require(properties.baseUrl(), "base-url");
        require(properties.onboardingPath(), "onboarding-path");
        require(properties.memberCode(), "member-code");
        require(properties.headerSecurityKey(), "header-security-key");
        require(properties.bodySecurityKey(), "body-security-key");
        if (!properties.baseUrl().startsWith("https://")) {
            throw new DfmConfigurationException(DfmMemberApiProperties.PREFIX + ".base-url must use https://");
        }
    }

    private static void require(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new DfmConfigurationException(DfmMemberApiProperties.PREFIX + "." + key + " is not configured");
        }
    }

    private String serialize(DfmOnboardingPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the DFM onboarding payload", e);
        }
    }

    private Mono<DfmOnboardingOutcome> classify(HttpStatusCode status, String body, String requestId) {
        DfmOnboardingResponse parsed = parse(body);
        String responseCode = parsed == null ? null : parsed.responseCode();
        String message = parsed == null ? null : parsed.responseMsg();
        log.info("DFM onboarding response: http_status={} response_code={} response_msg={} request_id={}",
                status.value(), responseCode, message, requestId);

        if (status.is2xxSuccessful()) {
            if (responseCode == null) {
                return badResponse();
            }
            if (isDuplicateInProgress(responseCode)) {
                return Mono.just(new DfmOnboardingOutcome.Rejected(responseCode, message, true));
            }
            if ("200".equals(responseCode)) {
                return Mono.just(new DfmOnboardingOutcome.Success(parsed));
            }
            return isClientErrorCode(responseCode)
                    ? Mono.just(new DfmOnboardingOutcome.Rejected(responseCode, message, false))
                    : badResponse();
        }
        if (status.value() == 401 || status.value() == 403) {
            log.error("DFM rejected credentials: http_status={} request_id={}", status.value(), requestId);
            return badResponse();
        }
        if (status.is4xxClientError()) {
            String code = responseCode != null ? responseCode : String.valueOf(status.value());
            return Mono.just(new DfmOnboardingOutcome.Rejected(code, message, status.value() == 409));
        }
        return badResponse();
    }

    private DfmOnboardingResponse parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, DfmOnboardingResponse.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private boolean isDuplicateInProgress(String responseCode) {
        return properties.duplicateInProgressCodes() != null && properties.duplicateInProgressCodes().contains(responseCode);
    }

    private static boolean isClientErrorCode(String responseCode) {
        try {
            int code = Integer.parseInt(responseCode.trim());
            return code >= 400 && code <= 499;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Emitted as an error (not a value) so the circuit breaker counts it as a technical failure. */
    private static Mono<DfmOnboardingOutcome> badResponse() {
        return Mono.error(new BadResponseException());
    }

    private static DfmOnboardingOutcome unavailable(Throwable error) {
        if (error instanceof CallNotPermittedException) {
            return new DfmOnboardingOutcome.Unavailable(DfmOnboardingOutcome.Reason.CIRCUIT_OPEN, error);
        }
        if (error instanceof TimeoutException || isConnectFailure(error)) {
            return new DfmOnboardingOutcome.Unavailable(DfmOnboardingOutcome.Reason.TIMEOUT, error);
        }
        return new DfmOnboardingOutcome.Unavailable(DfmOnboardingOutcome.Reason.BAD_RESPONSE, error);
    }

    /** Carries no DFM body: the response was already logged by status/code/message only. */
    private static final class BadResponseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BadResponseException() {
            super("DFM returned an invalid or failed response", null, false, false);
        }
    }
}
