package com.alramz.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.alramz.logging.config.SeqLoggingConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class SeqAppenderTest {

    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> apiKeys = new CopyOnWriteArrayList<>();
    private final AtomicInteger statusToReturn = new AtomicInteger(201);
    private final Logger logger = new LoggerContext().getLogger("com.alramz.test.Seq");
    private HttpServer server;
    private SeqAppender appender;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(SeqLoggingConfig.INGEST_PATH, exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                bodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            apiKeys.add(String.valueOf(exchange.getRequestHeaders().getFirst(SeqLoggingConfig.HEADER_API_KEY)));
            exchange.sendResponseHeaders(statusToReturn.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (appender != null) {
            appender.stop();
        }
        server.stop(0);
    }

    private SeqAppender enabledAppender() {
        SeqAppender a = new SeqAppender();
        a.setEnabled(true);
        a.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
        a.setServiceName("seq-test");
        a.setFlushIntervalMs(100);
        a.setMaxRetries(0);
        return a;
    }

    private LoggingEvent event(Level level, String message, Throwable t) {
        return event(level, message, t, Map.of());
    }

    private LoggingEvent event(Level level, String message, Throwable t, Map<String, String> mdc) {
        LoggingEvent e = new LoggingEvent(SeqAppenderTest.class.getName(), logger, level, message, t, null);
        e.setMDCPropertyMap(mdc);
        return e;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).as("condition met within timeout").isTrue();
    }

    @Test
    void settersClampToMinimumsAndGettersReflectConfiguration() {
        SeqAppender a = new SeqAppender();
        a.setUrl("http://seq:5341");
        a.setApiKey("k");
        a.setServiceName("svc");
        a.setBatchSize(0);
        a.setFlushIntervalMs(1);
        a.setQueueSize(-5);
        a.setConnectTimeoutMs(1);
        a.setRequestTimeoutMs(1);
        a.setMaxRetries(-1);
        a.setCircuitBreakerEnabled(true);
        a.setCircuitBreakerFailureThreshold(0);
        a.setCircuitBreakerCooldownMs(-10);

        assertThat(a.getUrl()).isEqualTo("http://seq:5341");
        assertThat(a.getApiKey()).isEqualTo("k");
        assertThat(a.getServiceName()).isEqualTo("svc");
        assertThat(a.isEnabled()).isFalse();
        assertThat(a.getBatchSize()).isEqualTo(1);
        assertThat(a.getFlushIntervalMs()).isEqualTo(100);
        assertThat(a.getQueueSize()).isEqualTo(1);
        assertThat(a.getConnectTimeoutMs()).isEqualTo(100);
        assertThat(a.getRequestTimeoutMs()).isEqualTo(100);
        assertThat(a.getMaxRetries()).isZero();
        assertThat(a.isCircuitBreakerEnabled()).isTrue();
        assertThat(a.getCircuitBreakerFailureThreshold()).isEqualTo(1);
        assertThat(a.getCircuitBreakerCooldownMs()).isZero();
    }

    @Test
    void disabledAppenderNeverStartsAndIgnoresEvents() {
        appender = new SeqAppender();
        appender.start();
        appender.doAppend(event(Level.INFO, "ignored", null));

        assertThat(appender.isStarted()).isFalse();
        assertThat(bodies).isEmpty();
    }

    @Test
    void shipsClefEventWithMdcMappingExceptionAndApiKey() throws Exception {
        appender = enabledAppender();
        appender.setApiKey("secret-key");
        appender.start();
        assertThat(appender.isStarted()).isTrue();

        LoggingEvent e = event(Level.ERROR, "boom happened", new IllegalStateException("bad state"),
                Map.of("correlationId", "c-1", "traceId", "t-1", "spanId", "s-1",
                        "responseStatus", "500", "userId", "u-9"));
        appender.doAppend(e);

        await(() -> !bodies.isEmpty());
        JsonNode node = new ObjectMapper().readTree(bodies.get(0).trim());
        assertThat(node.get("@mt").asText()).isEqualTo("boom happened");
        assertThat(node.get("@l").asText()).isEqualTo(SeqLoggingConfig.mapLevel(Level.ERROR));
        assertThat(node.get("@t").asText()).isNotBlank();
        assertThat(node.get("logger").asText()).isEqualTo("com.alramz.test.Seq");
        assertThat(node.get("correlationID").asText()).isEqualTo("c-1");
        assertThat(node.get("traceId").asText()).isEqualTo("t-1");
        assertThat(node.get("spanId").asText()).isEqualTo("s-1");
        assertThat(node.get("responseCode").asText()).isEqualTo("500");
        assertThat(node.get("userId").asText()).isEqualTo("u-9");
        assertThat(node.get("exceptionType").asText()).isEqualTo(IllegalStateException.class.getName());
        assertThat(node.get("exceptionMessage").asText()).isEqualTo("bad state");
        assertThat(node.get("exceptionStackTrace").asText()).startsWith("  at ");
        assertThat(apiKeys).containsExactly("secret-key");
    }

    @Test
    void omitsApiKeyHeaderWhenNotConfigured() throws Exception {
        appender = enabledAppender();
        appender.start();
        appender.doAppend(event(Level.INFO, "hello", null));

        await(() -> !apiKeys.isEmpty());
        assertThat(apiKeys).containsExactly("null");
        assertThat(new ObjectMapper().readTree(bodies.get(0).trim()).has("exceptionType")).isFalse();
    }

    @Test
    void circuitBreakerOpensAfterFailuresAndBlocksFurtherSends() throws Exception {
        statusToReturn.set(500);
        appender = enabledAppender();
        appender.setCircuitBreakerEnabled(true);
        appender.setCircuitBreakerFailureThreshold(1);
        appender.setCircuitBreakerCooldownMs(60_000);
        appender.start();

        appender.doAppend(event(Level.WARN, "first", null));
        await(() -> bodies.size() == 1);
        Thread.sleep(300);
        appender.doAppend(event(Level.WARN, "second", null));
        Thread.sleep(500);

        assertThat(bodies).hasSize(1);
        assertThat(bodies.get(0)).contains("first");
    }

    @Test
    void halfOpenCircuitRecoversOnSuccess() throws Exception {
        statusToReturn.set(503);
        appender = enabledAppender();
        appender.setCircuitBreakerEnabled(true);
        appender.setCircuitBreakerFailureThreshold(1);
        appender.setCircuitBreakerCooldownMs(0);
        appender.start();

        appender.doAppend(event(Level.INFO, "fails", null));
        await(() -> bodies.size() == 1);
        statusToReturn.set(200);
        Thread.sleep(200);
        appender.doAppend(event(Level.INFO, "recovers", null));
        await(() -> bodies.size() == 2);
        appender.doAppend(event(Level.INFO, "closed-again", null));

        await(() -> bodies.size() == 3);
        assertThat(bodies.get(2)).contains("closed-again");
    }

    @Test
    void retriesWithBackoffOnFailureStatus() throws Exception {
        statusToReturn.set(500);
        appender = enabledAppender();
        appender.setMaxRetries(1);
        appender.start();

        appender.doAppend(event(Level.INFO, "retry-me", null));

        await(() -> bodies.size() == 2);
        assertThat(bodies).allMatch(b -> b.contains("retry-me"));
    }

    @Test
    void unreachableEndpointIsSwallowedAndAppenderStops() throws Exception {
        appender = enabledAppender();
        appender.setUrl("http://127.0.0.1:1");
        appender.setConnectTimeoutMs(200);
        appender.start();

        appender.doAppend(event(Level.INFO, "lost", null));
        Thread.sleep(400);
        appender.stop();

        assertThat(appender.isStarted()).isFalse();
        assertThat(bodies).isEmpty();
    }

    @Test
    void stopWithoutStartIsSafe() {
        SeqAppender a = new SeqAppender();
        a.stop();
        assertThat(a.isStarted()).isFalse();
    }
}
