package com.alramz.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.alramz.logging.logback.AlramzJsonEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlramzJsonEncoderTest {

    static class CapturingAppender extends AppenderBase<ILoggingEvent> {
        final List<String> lines = new ArrayList<>();
        AlramzJsonEncoder encoder;

        @Override
        protected void append(ILoggingEvent event) {
            byte[] bytes = encoder.encode(event);
            if (bytes.length > 0) {
                lines.add(new String(bytes, StandardCharsets.UTF_8));
            }
        }
    }

    private LoggerContext ctx;
    private CapturingAppender appender;

    @BeforeEach
    void setUp() {
        ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender = new CapturingAppender();
        appender.setName("TestAppender");
        appender.setContext(ctx);
        appender.start();
        Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        root.detachAppender(appender);
        appender.stop();
    }

    @Test
    void classicModeProducesReadableLine() {
        AlramzJsonEncoder encoder = new AlramzJsonEncoder();
        encoder.setContext(ctx);
        encoder.setJson(false);
        encoder.setClassicPattern("[%-5level] %logger - %msg%n");
        encoder.start();
        appender.encoder = encoder;

        LoggerFactory.getLogger("encoder.test").info("hello world");

        assertThat(appender.lines).isNotEmpty();
        assertThat(appender.lines.get(0)).contains("hello world");
    }

    @Test
    void jsonModeProducesStructuredDocument() {
        AlramzJsonEncoder encoder = new AlramzJsonEncoder();
        encoder.setContext(ctx);
        encoder.setJson(true);
        encoder.setServiceName("data-validation-service");
        encoder.start();
        appender.encoder = encoder;

        LoggerFactory.getLogger("encoder.test").info("hello world");

        assertThat(appender.lines).isNotEmpty();
        String json = appender.lines.get(0);
        assertThat(json).contains("\"message\":\"hello world\"");
        assertThat(json).contains("\"level\":\"INFO\"");
        assertThat(json).contains("\"service\":\"data-validation-service\"");
    }
}
