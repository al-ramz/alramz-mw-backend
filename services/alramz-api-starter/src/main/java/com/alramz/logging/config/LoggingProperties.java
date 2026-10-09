package com.alramz.logging.config;

import com.alramz.logging.constants.LoggingConstants;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Strongly typed configuration bound to the {@code company.logging} namespace.
 * <p>
 * Child applications can override any value (or the whole configuration) simply
 * by setting properties in {@code application.yml}. The default values are
 * intentionally conservative: security-sensitive data is masked and payload
 * logging is disabled unless explicitly requested.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = LoggingProperties.PREFIX)
public class LoggingProperties {

    public static final String PREFIX = "company.logging";

    /**
     * Master switch. When {@code false} none of the filters, aspects or
     * interceptors are registered and MDC population is skipped.
     */
    private boolean enabled = true;

    private RequestProperties request = new RequestProperties();
    private ResponseProperties response = new ResponseProperties();
    private MaskingProperties masking = new MaskingProperties();
    private JsonProperties json = new JsonProperties();
    private AspectProperties aspect = new AspectProperties();
    private PerformanceProperties performance = new PerformanceProperties();
    private CorrelationIdProperties correlationId = new CorrelationIdProperties();
    private ExceptionProperties exception = new ExceptionProperties();

    /**
     * Paths that should be excluded from request/response logging.
     * Patterns use Spring Ant-style matching.
     */
    private List<String> excludedPaths = new ArrayList<>(List.of("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**"));

    public void setExcludedPaths(List<String> excludedPaths) {
        if (excludedPaths == null) {
            this.excludedPaths = new ArrayList<>(List.of("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**"));
        } else {
            this.excludedPaths = new ArrayList<>(excludedPaths);
        }
    }

    // ------------------------------------------------------------------ request

    @Getter
    @Setter
    public static class RequestProperties {
        private boolean enabled = true;
        /** Whether the selected request headers are logged. */
        private boolean includeHeaders = false;
        /** Whether the request payload (body) is logged. */
        private boolean includePayload = false;
        private int maxPayloadLength = 5000;
        /** Header names whose values must be masked when headers are logged. */
        private List<String> sensitiveHeaders = new ArrayList<>(List.of(
                "Authorization", "Cookie", "Set-Cookie", "X-API-Key", "api_key"));
        /** Header names to include when {@code includeHeaders} is true. Empty logs none. */
        private List<String> includedHeaders = new ArrayList<>();

        public void setSensitiveHeaders(List<String> sensitiveHeaders) {
            this.sensitiveHeaders = sensitiveHeaders == null ? new ArrayList<>() : new ArrayList<>(sensitiveHeaders);
        }

        public void setIncludedHeaders(List<String> includedHeaders) {
            this.includedHeaders = includedHeaders == null ? new ArrayList<>() : new ArrayList<>(includedHeaders);
        }
    }

    // ------------------------------------------------------------------ response

    @Getter
    @Setter
    public static class ResponseProperties {
        private boolean enabled = true;
        /** Whether the response payload (body) is logged. */
        private boolean includePayload = false;
        private int maxPayloadLength = 5000;
    }

    // ------------------------------------------------------------------ masking

    @Getter
    @Setter
    public static class MaskingProperties {
        private boolean enabled = true;
        /** Keys/patterns (case-insensitive) whose values are masked in log output. */
        private List<String> sensitiveKeys = new ArrayList<>(List.of(
                "password", "token", "authorization", "accessToken", "refreshToken",
                "jwt", "aadhaar", "pan", "ssn", "creditCard", "cvv", "apiKey",
                "secret", "session", "cookie"));
        /** Additional custom regex patterns to mask. */
        private List<String> customPatterns = new ArrayList<>();
        /** Replacement mask. */
        private String maskReplacement = "********";
    }

    // ------------------------------------------------------------------ json

    @Getter
    @Setter
    public static class JsonProperties {
        /** Whether structured JSON logging is enabled. */
        private boolean enabled = false;
        private boolean prettyPrint = false;
    }

    // ------------------------------------------------------------------ aspect

    @Getter
    @Setter
    public static class AspectProperties {
        private boolean enabled = false;
        private boolean logEntry = true;
        private boolean logExit = true;
        private boolean logArgs = true;
        private boolean logReturn = true;
        private boolean logExceptions = true;
        private int maxPayloadLength = 1000;
        private String separator = "========== ";
        private List<String> excludedPackages = new ArrayList<>(List.of(
                "org.springframework.", "org.hibernate.", "java.", "javax.", "jakarta.", "sun.", "com.sun."
        ));
        private List<String> excludedClassPatterns = new ArrayList<>(List.of(
                "$$EnhancerBySpringCGLIB$$", "$$FastClassBySpringCGLIB$$"
        ));
    }

    // ------------------------------------------------------------------ performance

    @Getter
    @Setter
    public static class PerformanceProperties {
        /**
         * Threshold expressed as a duration string (e.g. {@code 500ms},
         * {@code 2s}). Operations completing within the threshold are not logged.
         */
        private Duration threshold = Duration.ofMillis(500);
    }

    // ------------------------------------------------------------------ correlation id

    @Getter
    @Setter
    public static class CorrelationIdProperties {
        private String header = LoggingConstants.DEFAULT_CORRELATION_ID_HEADER;
        private String requestIdHeader = LoggingConstants.DEFAULT_REQUEST_ID_HEADER;
        private String userIdHeader = LoggingConstants.DEFAULT_USER_ID_HEADER;
        private String tenantIdHeader = LoggingConstants.DEFAULT_TENANT_ID_HEADER;
        /** Whether the correlation ID is added to the HTTP response header. */
        private boolean responseHeader = true;
    }

    // ------------------------------------------------------------------ exception

    @Getter
    @Setter
    public static class ExceptionProperties {
        private boolean enabled = true;
    }

    // ------------------------------------------------------------------ database logging

    @Getter
    @Setter
    public static class DatabaseLoggingProperties {
        /** Master switch for DB-backed API audit logging. */
        private boolean enabled = false;
        /** Whether to include request payload in audit log. */
        private boolean includeRequestPayload = false;
        /** Whether to include response payload in audit log. */
        private boolean includeResponsePayload = false;
        /** Cron expression for cleanup of expired audit logs. */
        private String cleanupCron = "0 0 2 * * *";
        /** Maximum age of audit log entries in days. */
        private int retentionDays = 7;
        /**
         * Also audit outgoing WebClient calls made through {@code alramzWebClientLoggingFilterFunction}.
         * Off by default so a service opts in explicitly.
         */
        private boolean outboundEnabled = false;
        /** Endpoints to exclude from audit logging. */
        private List<String> excludedPaths = new ArrayList<>(List.of(
                "/api/v1/info", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**"));
    }

    // ------------------------------------------------------------------ seq

    @Getter
    @Setter
    public static class CircuitBreakerProperties {
        private boolean enabled = false;
        private int failureThreshold = 3;
        private long cooldownMs = 10000L;
    }

    @Getter
    @Setter
    public static class SeqProperties {
        private boolean enabled = false;
        private String url = "http://localhost:5341";
        private String apiKey = "";
        private int batchSize = 50;
        private int flushIntervalMs = 1000;
        private int queueSize = 50000;
        private int connectTimeoutMs = 3000;
        private int requestTimeoutMs = 5000;
        private int maxRetries = 3;
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    }

    private DatabaseLoggingProperties databaseLogging = new DatabaseLoggingProperties();
    private SeqProperties seq = new SeqProperties();
}
