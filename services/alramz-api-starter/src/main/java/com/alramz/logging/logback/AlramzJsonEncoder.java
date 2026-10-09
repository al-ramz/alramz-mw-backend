package com.alramz.logging.logback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Logback {@link ch.qos.logback.core.Encoder} that emits either a classic,
 * human-readable line (driven by an internal {@link PatternLayout}) or a
 * structured JSON document. The active mode and the service name are configured
 * from {@code logback-spring.xml} (sourced from Spring properties), so no
 * external service is required.
 * <p>
 * JSON fields: {@code timestamp, level, service, logger, thread,
 * correlationId, traceId, spanId} (from MDC) followed by every remaining MDC
 * entry, then {@code message} and {@code exception}.
 */
@Setter
@Getter
@Slf4j
public class AlramzJsonEncoder extends EncoderBase<ILoggingEvent> {

    /**
     * Classic (non-JSON) log pattern. Defaults to a line that already carries the
     * most relevant diagnostic context entries.
     */
    private String classicPattern = "[%d{yyyy-MM-dd HH:mm:ss.SSS}] [%thread] %-5level %logger{36} - %msg%n";

    /** Fully qualified service name pulled from {@code spring.application.name}. */
    private String serviceName = "unknown";

    /** Whether JSON (structured) output is produced. */
    private boolean json = false;

    /** Whether to pretty-print JSON output. */
    private boolean prettyPrint = false;

    private ObjectMapper objectMapper;
    private PatternLayout classicLayout;
    private DateTimeFormatter timestampFormatter;

    public AlramzJsonEncoder() {
        this.objectMapper = new ObjectMapper();
        this.timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                .withZone(ZoneId.systemDefault());
    }

    @Override
    public void start() {
        if (json) {
            try {
                this.objectMapper.findAndRegisterModules();
            } catch (Exception e) { // NOPMD AvoidCatchingGenericException
                log.warn("Unable to register Jackson modules for JSON logging", e);
            }
        } else {
            this.classicLayout = new PatternLayout();
            this.classicLayout.setContext(getContext());
            this.classicLayout.setPattern(classicPattern);
            this.classicLayout.setOutputPatternAsHeader(false);
            this.classicLayout.start();
        }
        super.start();
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        if (json) {
            try {
                return toJson(event);
            } catch (JsonProcessingException e) {
                // Fall back to a minimal string to never lose a log line.
                return fallback(event, e);
            }
        }
        if (classicLayout != null) {
            return classicLayout.doLayout(event).getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    @Override
    public byte[] headerBytes() {
        return new byte[0];
    }

    @Override
    public byte[] footerBytes() {
        return new byte[0];
    }

    @Override
    public void stop() {
        if (classicLayout != null) {
            classicLayout.stop();
        }
        super.stop();
    }

    private byte[] toJson(ILoggingEvent event) throws JsonProcessingException {
        ObjectNode node = objectMapper.getNodeFactory().objectNode();
        node.put("timestamp", formatInstant(event.getTimeStamp()));
        node.put("level", event.getLevel().toString()); // NOPMD LawOfDemeter
        node.put("service", serviceName);
        node.put("logger", event.getLoggerName());
        node.put("thread", event.getThreadName());

        Map<String, String> mdc = event.getMDCPropertyMap();
        putIfPresent(node, "correlationId", mdc.get("correlationId"));
        putIfPresent(node, "traceId", mdc.get("traceId"));
        putIfPresent(node, "spanId", mdc.get("spanId"));
        for (Map.Entry<String, String> entry : mdc.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || node.has(key)) {
                continue;
            }
            putIfPresent(node, key, entry.getValue());
        }

        node.put("message", event.getFormattedMessage());

        var tp = event.getThrowableProxy(); // NOPMD LawOfDemeter
        if (tp != null) {
            String exception = tp.getClassName();
            if (tp.getMessage() != null) {
                exception += ": " + tp.getMessage();
            }
            node.put("exception", exception);
        }

        String json = prettyPrint
                ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
                : objectMapper.writeValueAsString(node);
        return (json + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
    }

    private void putIfPresent(ObjectNode node, String key, String value) {
        if (value != null && !value.isBlank()) {
            node.put(key, value);
        }
    }

    private String formatInstant(long epochMillis) {
        ZonedDateTime zdt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault());
        return timestampFormatter.format(zdt);
    }

    private byte[] fallback(ILoggingEvent event, Exception cause) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatInstant(event.getTimeStamp()))
                .append(" ")
                .append(event.getLevel())
                .append(" [").append(serviceName).append("] ")
                .append(event.getFormattedMessage());
        return (sb.toString() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
    }
}
