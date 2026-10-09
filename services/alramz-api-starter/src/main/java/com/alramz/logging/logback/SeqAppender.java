package com.alramz.logging.logback;

import com.alramz.logging.config.SeqLoggingConfig;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class SeqAppender extends AppenderBase<ILoggingEvent> {

    private static final Logger auditLogger = LoggerFactory.getLogger(SeqAppender.class);

    // Configuration fields (populated by logback from logback-spring.xml setters)
    private String url = SeqLoggingConfig.DEFAULT_URL;
    private String apiKey = SeqLoggingConfig.DEFAULT_API_KEY;
    private boolean enabled = false;
    private String serviceName = "application";

    private int batchSize = SeqLoggingConfig.DEFAULT_BATCH_SIZE;
    private int flushIntervalMs = SeqLoggingConfig.DEFAULT_FLUSH_INTERVAL_MS;
    private int queueSize = SeqLoggingConfig.DEFAULT_QUEUE_SIZE;
    private int connectTimeoutMs = SeqLoggingConfig.DEFAULT_CONNECT_TIMEOUT_MS;
    private int requestTimeoutMs = SeqLoggingConfig.DEFAULT_REQUEST_TIMEOUT_MS;
    private int maxRetries = SeqLoggingConfig.DEFAULT_MAX_RETRIES;

    private boolean circuitBreakerEnabled = SeqLoggingConfig.DEFAULT_CIRCUIT_BREAKER_ENABLED;
    private int circuitBreakerFailureThreshold = SeqLoggingConfig.DEFAULT_FAILURE_THRESHOLD;
    private long circuitBreakerCooldownMs = SeqLoggingConfig.DEFAULT_COOLDOWN_MS;

    // Runtime state
    private transient URI ingestUri;
    private transient HttpClient httpClient;
    private transient ObjectMapper objectMapper;
    private transient java.util.concurrent.BlockingQueue<ILoggingEvent> queue;

    private enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    private transient volatile CircuitState circuitState = CircuitState.CLOSED;
    private transient volatile int failureCount = 0;
    private transient volatile long lastFailureTime = 0L;

    private transient Thread flushThread;
    private transient volatile boolean running = false;

    @Override
    public void start() {
        if (!enabled) {
            return;
        }
        super.start();
        this.objectMapper = new ObjectMapper();
        this.queue = new java.util.concurrent.LinkedBlockingQueue<>(queueSize);
        this.ingestUri = URI.create(url + SeqLoggingConfig.INGEST_PATH);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.running = true;
        this.circuitState = CircuitState.CLOSED;
        this.failureCount = 0;
        this.lastFailureTime = 0L;
        this.flushThread = new Thread(this::flushLoop, "seq-flush-" + serviceName);
        this.flushThread.setDaemon(true);
        this.flushThread.start();
        auditLogger.info("SeqAppender started: url={}, batchSize={}, flushIntervalMs={}, queueSize={}, " +
                "circuitBreakerEnabled={}", url, batchSize, flushIntervalMs, queueSize, circuitBreakerEnabled);
    }

    @Override
    public void stop() {
        if (!running) {
            super.stop();
            return;
        }
        running = false;
        if (flushThread != null) {
            flushThread.interrupt();
            try {
                flushThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        drainQueue();
        if (httpClient != null) {
            httpClient = null;
        }
        super.stop();
        auditLogger.info("SeqAppender stopped");
    }

    @Override
    public void append(ILoggingEvent event) {
        if (!isStarted() || !enabled) {
            return;
        }
        try {
            queue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            auditLogger.warn("Interrupted while queueing log event for Seq", e);
        }
    }

    private void drainQueue() {
        java.util.List<ILoggingEvent> batch = new java.util.ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        if (!batch.isEmpty()) {
            flushBatch(batch, false);
        }
    }

    private void flushLoop() {
        while (running) {
            try {
                java.util.List<ILoggingEvent> batch = new java.util.ArrayList<>(batchSize);
                ILoggingEvent head = queue.poll(flushIntervalMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (head != null) {
                    batch.add(head);
                    queue.drainTo(batch, batchSize - 1);
                }
                if (!batch.isEmpty()) {
                    flushBatch(batch, true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                auditLogger.warn("Unexpected error in Seq flush loop", e);
            }
        }
    }

    /**
     * Optimized batch flushing with pre-sized StringBuilder to reduce memory allocations.
     * Pre-sizing estimate: ~500 bytes per event + overhead.
     */
    private void flushBatch(java.util.List<ILoggingEvent> batch, boolean retryOnFailure) {
        if (!canSend()) {
            return;
        }

        StringBuilder payload = new StringBuilder(Math.max(256, batch.size() * 520));
        for (ILoggingEvent event : batch) {
            ObjectNode eventNode = buildEventNode(event);
            try {
                payload.append(objectMapper.writeValueAsString(eventNode)).append('\n');
            } catch (JsonProcessingException e) {
                auditLogger.warn("Failed to serialize Seq log event", e);
            }
        }

        if (payload.length() > 0) {
            postWithRetry(payload.toString(), retryOnFailure);
        }
    }

    private boolean canSend() {
        if (!circuitBreakerEnabled) {
            return true;
        }
        long now = System.currentTimeMillis();
        switch (circuitState) {
            case CLOSED:
                return true;
            case OPEN:
                if (now - lastFailureTime >= circuitBreakerCooldownMs) {
                    circuitState = CircuitState.HALF_OPEN;
                    return true;
                }
                return false;
            case HALF_OPEN:
                return true;
            default:
                return true;
        }
    }

    private void recordSuccess() {
        if (!circuitBreakerEnabled) {
            return;
        }
        if (circuitState != CircuitState.CLOSED) {
            circuitState = CircuitState.CLOSED;
            failureCount = 0;
            lastFailureTime = 0L;
        }
    }

    private void recordFailure() {
        if (!circuitBreakerEnabled) {
            return;
        }
        failureCount++;
        lastFailureTime = System.currentTimeMillis();
        if (failureCount >= circuitBreakerFailureThreshold) {
            circuitState = CircuitState.OPEN;
            auditLogger.warn("Seq circuit breaker opened after {} failures", failureCount);
        }
    }

    /**
     * Sends payload with exponential backoff retry strategy.
     * Backoff progression: 1s → 2s → 4s → (capped at 5s)
     */
    private void postWithRetry(String payload, boolean retryOnFailure) {
        int attempt = 0;
        long backoff = SeqLoggingConfig.BASE_BACKOFF_MS;

        while (attempt <= maxRetries) {
            try {
                HttpRequest request = buildHttpRequest(payload);
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

                if (isSuccessResponse(response.statusCode())) {
                    recordSuccess();
                    return;
                }
                auditLogger.warn("Seq ingestion returned status {} on attempt {}", response.statusCode(), attempt + 1);
            } catch (Exception e) {
                auditLogger.warn("Seq ingestion failed on attempt {}: {}", attempt + 1, e.getMessage());
            }

            attempt++;
            if (attempt > maxRetries || !retryOnFailure) {
                break;
            }

            try {
                Thread.sleep(backoff);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }

            backoff = Math.min(backoff * 2, SeqLoggingConfig.MAX_BACKOFF_MS);
        }
        recordFailure();
    }

    private boolean isSuccessResponse(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private HttpRequest buildHttpRequest(String payload) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(ingestUri)
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .header("Content-Type", SeqLoggingConfig.CONTENT_TYPE);

        if (!apiKey.isEmpty()) {
            builder.header(SeqLoggingConfig.HEADER_API_KEY, apiKey);
        }

        return builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build();
    }

    /**
     * Builds ObjectNode for a single logging event with O(1) level mapping
     * and optimized exception field extraction.
     */
    private ObjectNode buildEventNode(ILoggingEvent event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("@t", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        node.put("@mt", event.getFormattedMessage());
        node.put("@l", SeqLoggingConfig.mapLevel(event.getLevel()));
        node.put("thread", event.getThreadName());
        node.put("logger", event.getLoggerName());

        Map<String, String> mdcContext = event.getMDCPropertyMap();
        if (mdcContext != null && !mdcContext.isEmpty()) {
            addMdcFields(node, mdcContext);
        }

        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            addExceptionFields(node, throwableProxy);
        }

        return node;
    }

    private void addMdcFields(ObjectNode node, Map<String, String> mdcContext) {
        for (Map.Entry<String, String> entry : mdcContext.entrySet()) {
            String value = entry.getValue();
            if (value == null) {
                continue;
            }
            String key = entry.getKey();
            switch (key) {
                case "correlationId" -> node.put("correlationID", value);
                case "traceId" -> node.put("traceId", value);
                case "spanId" -> node.put("spanId", value);
                case "responseStatus" -> node.put("responseCode", value);
                default -> node.put(key, value);
            }
        }
    }

    /**
     * Optimized exception field extraction with pre-sized StringBuilder.
     * Avoids unnecessary null checks and proxy array iteration.
     */
    private void addExceptionFields(ObjectNode node, IThrowableProxy throwableProxy) {
        node.put("exceptionType", throwableProxy.getClassName());

        String exceptionMessage = throwableProxy.getMessage();
        if (exceptionMessage != null) {
            node.put("exceptionMessage", exceptionMessage);
        }

        ch.qos.logback.classic.spi.StackTraceElementProxy[] proxies = throwableProxy.getStackTraceElementProxyArray();
        if (proxies != null && proxies.length > 0) {
            StringBuilder stackTrace = new StringBuilder(proxies.length * 80);
            for (ch.qos.logback.classic.spi.StackTraceElementProxy proxy : proxies) {
                if (proxy != null && proxy.getStackTraceElement() != null) {
                    stackTrace.append("  at ").append(proxy.getStackTraceElement()).append('\n');
                }
            }
            if (stackTrace.length() > 0) {
                stackTrace.setLength(stackTrace.length() - 1);
                node.put("exceptionStackTrace", stackTrace.toString());
            }
        }
    }

    // Setters (used by logback configuration)
    public void setUrl(String url) {
        this.url = url;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }

    public void setFlushIntervalMs(int flushIntervalMs) {
        this.flushIntervalMs = Math.max(100, flushIntervalMs);
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = Math.max(1, queueSize);
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = Math.max(100, connectTimeoutMs);
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = Math.max(100, requestTimeoutMs);
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
    }

    public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = Math.max(1, circuitBreakerFailureThreshold);
    }

    public void setCircuitBreakerCooldownMs(long circuitBreakerCooldownMs) {
        this.circuitBreakerCooldownMs = Math.max(0, circuitBreakerCooldownMs);
    }

    // Getters (for testing and diagnostics)
    public String getUrl() {
        return url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }

    public long getCircuitBreakerCooldownMs() {
        return circuitBreakerCooldownMs;
    }
}
