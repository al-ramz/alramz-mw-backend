package com.alramz.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.LogMaskingUtil;
import com.alramz.logging.util.MDCUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiAuditLogServiceTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final LoggingProperties properties = new LoggingProperties();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiAuditLogService.class);
    private ApiAuditLogService service;

    @BeforeEach
    void setUp() {
        LogMaskingUtil.configure(true, "********", List.of("password"), List.of());
        service = new ApiAuditLogService(jdbc, new ObjectMapper(), properties,
                new MockEnvironment().withProperty("spring.profiles.active", "qa"));
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        MDCUtil.clear();
    }

    private static ApiAuditLog entry(Object request, Object response) {
        return new ApiAuditLog(UUID.fromString("00000000-0000-0000-0000-000000000001"), "INBOUND", "svc",
                "Ctrl", "/api/x", "POST", request, response, "SUCCESS", 200, null, null, 12L,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void logPersistsMaskedJsonAndEmitsAuditEventWithMdc() {
        service.log(entry(Map.of("consumer", "app1", "password", "pw"), List.of("ok")));

        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).update(anyString(), params.capture());
        Map<String, Object> p = params.getValue();
        assertThat(p).containsEntry("direction", "INBOUND")
                .containsEntry("serviceName", "svc")
                .containsEntry("apiEndpoint", "/api/x")
                .containsEntry("statusCode", 200)
                .containsEntry("durationMs", 12L)
                .containsEntry("createdAt", Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")))
                .containsEntry("response", "[\"ok\"]");
        assertThat((String) p.get("request")).contains("\"password\":\"***\"").contains("\"consumer\":\"app1\"");

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).contains("direction=INBOUND", "endpoint=/api/x");
        assertThat(event.getMDCPropertyMap()).containsEntry("Consumer", "app1")
                .containsEntry("Environment", "qa")
                .containsEntry("StatusCode", "200");
        assertThat(MDCUtil.get("Consumer")).isNull();
    }

    @Test
    void consumerIsExtractedFromJsonStringRequest() {
        service.log(entry("{\"consumer\":\"app2\"}", null));

        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("Consumer", "app2");
    }

    @Test
    void unparsableOrConsumerlessRequestYieldsNoConsumer() {
        service.log(entry("not json", null));
        service.log(entry(Map.of("other", 1), null));
        service.log(entry(42, null));

        assertThat(appender.list).hasSize(3)
                .allSatisfy(e -> assertThat(e.getMDCPropertyMap()).doesNotContainKey("Consumer"));
    }

    @Test
    void databaseFailureIsSwallowed() {
        when(jdbc.update(anyString(), anyMap())).thenThrow(new IllegalStateException("db down"));

        service.log(entry(null, null));

        assertThat(appender.list).anySatisfy(e -> assertThat(e.getFormattedMessage())
                .isEqualTo("Failed to insert api_audit_log"));
    }

    @Test
    void cleanupDeletesUsingRetentionDays() {
        properties.getDatabaseLogging().setRetentionDays(30);
        when(jdbc.update(anyString(), anyMap())).thenReturn(4);

        service.cleanupExpired();

        verify(jdbc).update(anyString(), eq(Map.of("retentionDays", 30)));
        assertThat(appender.list.get(0).getFormattedMessage()).contains("Cleaned up 4", "retention=30");
    }

    @Test
    void cleanupFailureIsSwallowed() {
        when(jdbc.update(anyString(), anyMap())).thenThrow(new IllegalStateException("x"));

        service.cleanupExpired();

        assertThat(appender.list.get(0).getFormattedMessage()).startsWith("Failed to cleanup");
        assertThat(service.getMasker()).isNotNull();
    }
}
