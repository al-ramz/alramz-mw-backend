package com.alramz.audit;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.LogMaskingUtil;
import com.alramz.logging.util.MDCUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebClientAuditFilterTest {

    private static final String CORRELATION_ID = "2afe4a42-44f7-4e12-aae7-df0e42d4d757";

    private final ApiAuditLogService auditLogService = mock(ApiAuditLogService.class);
    private final LoggingProperties properties = new LoggingProperties();

    @BeforeEach
    void setUp() {
        LogMaskingUtil.configure(true, null, List.of("password"), List.of());
        when(auditLogService.getMasker()).thenReturn(new SensitiveDataMasker(new ObjectMapper(), true));
        properties.getDatabaseLogging().setIncludeRequestPayload(true);
        properties.getDatabaseLogging().setIncludeResponsePayload(true);
        MDCUtil.putCorrelationId(CORRELATION_ID);
    }

    @AfterEach
    void tearDown() {
        LogMaskingUtil.configure(true, null, List.of(), List.of());
        MDCUtil.clear();
    }

    /** Serializes the request like a real connector would, then answers with {@code response}. */
    private static ExchangeFunction answering(HttpStatus status, String body) {
        return request -> request
                .writeTo(new MockClientHttpRequest(request.method(), request.url()), ExchangeStrategies.withDefaults())
                .then(Mono.just(ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()));
    }

    private WebClient client(ExchangeFunction transport, Scheduler scheduler) {
        return WebClient.builder()
                .exchangeFunction(transport)
                .filter(new WebClientAuditFilter(auditLogService, properties, "test-service", scheduler))
                .build();
    }

    private WebClient client(ExchangeFunction transport) {
        return client(transport, Schedulers.immediate());
    }

    private Mono<String> post(WebClient client) {
        return client.post().uri("https://finoux.example.invalid/api/market?apiKey=secret-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("user", "admin", "password", "req-secret"))
                .retrieve()
                .bodyToMono(String.class);
    }

    private ApiAuditLog recordedEntry() {
        ArgumentCaptor<ApiAuditLog> entry = ArgumentCaptor.forClass(ApiAuditLog.class);
        verify(auditLogService).log(entry.capture());
        return entry.getValue();
    }

    @Test
    void recordsASuccessfulCallAndStillHandsTheBodyToTheCaller() {
        String body = post(client(answering(HttpStatus.OK, "{\"isSuccess\":true,\"password\":\"resp-secret\"}"))).block();

        assertThat(body).isEqualTo("{\"isSuccess\":true,\"password\":\"resp-secret\"}");
        ApiAuditLog entry = recordedEntry();
        assertThat(entry.correlationId().toString()).isEqualTo(CORRELATION_ID);
        assertThat(entry.direction()).isEqualTo("OUTBOUND");
        assertThat(entry.serviceName()).isEqualTo("test-service");
        assertThat(entry.controllerName()).isEqualTo("finoux.example.invalid");
        assertThat(entry.apiEndpoint()).isEqualTo("https://finoux.example.invalid/api/market");
        assertThat(entry.method()).isEqualTo(HttpMethod.POST.name());
        assertThat(entry.status()).isEqualTo("SUCCESS");
        assertThat(entry.statusCode()).isEqualTo(200);
        assertThat(entry.exceptionCause()).isNull();
        assertThat(entry.durationMs()).isNotNegative();
        assertThat((String) entry.request()).contains("admin").doesNotContain("req-secret");
        assertThat((String) entry.response()).contains("isSuccess").doesNotContain("resp-secret");
    }

    @Test
    void recordsAnErrorStatusAsFailure() {
        assertThatThrownBy(() -> post(client(answering(HttpStatus.INTERNAL_SERVER_ERROR, "{\"message\":\"boom\"}"))).block());

        ApiAuditLog entry = recordedEntry();
        assertThat(entry.status()).isEqualTo("FAILURE");
        assertThat(entry.statusCode()).isEqualTo(500);
        assertThat(entry.exceptionCause()).isEqualTo("500 INTERNAL_SERVER_ERROR");
        assertThat((String) entry.response()).contains("boom");
    }

    @Test
    void recordsAConnectionFailure() {
        ExchangeFunction refused = request -> Mono.error(new ConnectException("Connection refused"));

        assertThatThrownBy(() -> post(client(refused)).block());

        ApiAuditLog entry = recordedEntry();
        assertThat(entry.status()).isEqualTo("FAILURE");
        assertThat(entry.statusCode()).isNull();
        assertThat(entry.exceptionCause()).isEqualTo("Connection refused");
        assertThat(entry.exceptionClass()).isEqualTo(ConnectException.class.getName());
    }

    @Test
    void recordsATimeoutAsACancelledCall() {
        ExchangeFunction silent = request -> Mono.never();

        assertThatThrownBy(() -> post(client(silent)).timeout(Duration.ofMillis(100)).block());

        ApiAuditLog entry = recordedEntry();
        assertThat(entry.status()).isEqualTo("FAILURE");
        assertThat(entry.statusCode()).isNull();
        assertThat(entry.exceptionCause()).isEqualTo(WebClientAuditFilter.CANCELLED);
    }

    @Test
    void leavesPayloadsOutWhenTheyAreNotEnabled() {
        properties.getDatabaseLogging().setIncludeRequestPayload(false);
        properties.getDatabaseLogging().setIncludeResponsePayload(false);

        String body = post(client(answering(HttpStatus.OK, "{\"ok\":true}"))).block();

        assertThat(body).isEqualTo("{\"ok\":true}");
        ApiAuditLog entry = recordedEntry();
        assertThat(entry.request()).isNull();
        assertThat(entry.response()).isNull();
        assertThat(entry.statusCode()).isEqualTo(200);
    }

    @Test
    void usesAFreshCorrelationIdWhenTheRequestOneIsNotAUuid() {
        MDCUtil.putCorrelationId("not-a-uuid");

        post(client(answering(HttpStatus.OK, "{}"))).block();

        assertThat(recordedEntry().correlationId()).isNotNull();
    }

    @Test
    void neverFailsTheCallWhenTheAuditWriteCannotBeScheduled() {
        Scheduler full = mock(Scheduler.class);
        when(full.schedule(any())).thenThrow(new RejectedExecutionException("queue full"));

        String body = post(client(answering(HttpStatus.OK, "{\"ok\":true}"), full)).block();

        assertThat(body).isEqualTo("{\"ok\":true}");
        verify(auditLogService, never()).log(any());
    }
}
