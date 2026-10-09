package com.alramz.audit;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.MDCUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.annotation.NewSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ApiAuditLogService {

    private static final String SQL = """
            INSERT INTO api_audit_log
            (correlation_id, direction, service_name, controller_name, api_endpoint, method,
             request, response, status, status_code, exception_cause, exception_class, duration_ms, created_at)
            VALUES (:correlationId, :direction, :serviceName, :controllerName, :apiEndpoint, :method,
                    CAST(:request AS JSON), CAST(:response AS JSON), :status, :statusCode, :exceptionCause, :exceptionClass, :durationMs, :createdAt)
            """;

    private static final String CLEANUP_SQL = "DELETE FROM api_audit_log WHERE created_at < NOW() - INTERVAL ? * INTERVAL '1 day'";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LoggingProperties properties;
    private final SensitiveDataMasker masker;
    private final Environment environment;

    public ApiAuditLogService(@Qualifier("middlewareNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper,
                              LoggingProperties properties,
                              Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.properties = properties;
        this.masker = new SensitiveDataMasker(objectMapper, properties.getMasking().isEnabled()); // NOPMD LawOfDemeter
        this.environment = environment;
    }

    public void log(ApiAuditLog entry) {
        try {
            // Mask and serialize once, reuse results (eliminates duplicate processing)
            Object maskedRequest = masker.mask(entry.request());
            Object maskedResponse = masker.mask(entry.response());
            String requestJson = toJson(maskedRequest);
            String responseJson = toJson(maskedResponse);

            // Build parameters map with initial capacity hint
            Map<String, Object> params = new HashMap<>(16);
            params.put("correlationId", entry.correlationId());
            params.put("direction", entry.direction());
            params.put("serviceName", entry.serviceName());
            params.put("controllerName", entry.controllerName());
            params.put("apiEndpoint", entry.apiEndpoint());
            params.put("method", entry.method());
            params.put("request", requestJson);
            params.put("response", responseJson);
            params.put("status", entry.status());
            params.put("statusCode", entry.statusCode());
            params.put("exceptionCause", entry.exceptionCause());
            params.put("exceptionClass", entry.exceptionClass());
            params.put("durationMs", entry.durationMs());
            params.put("createdAt", Timestamp.from(entry.createdAt()));

            jdbcTemplate.update(SQL, params);
            logAuditEvent(entry, requestJson, responseJson);
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            log.error("Failed to insert api_audit_log", e);
        }
    }

    private void logAuditEvent(ApiAuditLog entry, String requestJson, String responseJson) {
        // Use try-with-resources style MDC management for cleaner code
        String[] mdcKeys = {"Direction", "ServiceName", "Controller", "Endpoint", "Method",
                            "StatusCode", "DurationMs", "ExceptionCause", "ExceptionClass",
                            "Environment", "Request", "Response", "Consumer"};
        String consumerValue = extractConsumer(entry.request());
        String[] mdcValues = {
            entry.direction(),
            entry.serviceName(),
            entry.controllerName(),
            entry.apiEndpoint(),
            entry.method(),
            String.valueOf(entry.statusCode()),
            String.valueOf(entry.durationMs()),
            entry.exceptionCause(),
            entry.exceptionClass(),
            environment.getProperty("spring.profiles.active"),
            requestJson,
            responseJson,
            consumerValue
        };

        // Put all values
        for (int i = 0; i < mdcKeys.length; i++) {
            MDCUtil.put(mdcKeys[i], mdcValues[i]);
        }

        try {
            log.info("--> API Audit: direction={} service={} endpoint={} status={} statusCode={}",
                    entry.direction(), entry.serviceName(), entry.apiEndpoint(), entry.status(), entry.statusCode());
        } finally {
            // Remove all values
            for (String key : mdcKeys) {
                MDCUtil.remove(key);
            }
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            log.warn("Failed to serialize audit payload to JSON", e);
            return null;
        }
    }

    private String extractConsumer(Object request) {
        if (request instanceof Map<?, ?> map) {
            Object raw = map.get("consumer");
            if (raw != null) {
                return raw.toString();
            }
            return null;
        }
        if (request instanceof String str) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(str);
                com.fasterxml.jackson.databind.JsonNode consumer = node.get("consumer");
                if (consumer != null && !consumer.isMissingNode()) {
                    return consumer.asText();
                }
            } catch (Exception e) { // NOPMD AvoidCatchingGenericException
                // ignore parse errors
            }
        }
        return null;
    }

    @NewSpan("api.audit.cleanup")
    @Scheduled(cron = "${company.logging.database-logging.cleanup-cron:0 0 2 * * *}")
    public void cleanupExpired() {
        try {
            LoggingProperties.DatabaseLoggingProperties databaseLogging = properties.getDatabaseLogging();
            int retentionDays = databaseLogging.getRetentionDays();
            int deleted = jdbcTemplate.update(CLEANUP_SQL, Map.of("retentionDays", retentionDays));
            log.info("Cleaned up {} expired api_audit_log entries (retention={} days)", deleted, retentionDays);
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            log.error("Failed to cleanup expired api_audit_log entries", e);
        }
    }

    public SensitiveDataMasker getMasker() {
        return masker;
    }
}
