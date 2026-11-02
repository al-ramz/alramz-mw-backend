package com.alramz.service;

import com.alramz.config.DmsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class OpenBeeTimestampConverterTest {

    private OpenBeeTimestampConverter converter;

    @BeforeEach
    void setUp() {
        DmsProperties properties = new DmsProperties();
        properties.getOpenbee().setTimeZone(ZoneId.of("Asia/Dubai"));
        converter = new OpenBeeTimestampConverter(properties);
    }

    @Test
    void localTimestamp_isPlacedInTheConfiguredZone() {
        OffsetDateTime converted = converter.toOffsetDateTime("2026-10-28T10:15:00", new AtomicBoolean());

        assertThat(converted).isEqualTo(OffsetDateTime.parse("2026-10-28T10:15:00+04:00"));
        assertThat(converted.toString()).isEqualTo("2026-10-28T10:15+04:00");
    }

    @Test
    void spaceSeparatedAndOffsetTimestamps_areAccepted() {
        assertThat(converter.toOffsetDateTime("2026-10-28 10:15:00", new AtomicBoolean()))
                .isEqualTo(OffsetDateTime.parse("2026-10-28T10:15:00+04:00"));
        assertThat(converter.toOffsetDateTime("2026-10-28T06:15:00Z", new AtomicBoolean()))
                .isEqualTo(OffsetDateTime.parse("2026-10-28T06:15:00Z"));
    }

    @Test
    void absent_isNull() {
        assertThat(converter.toOffsetDateTime(null, new AtomicBoolean())).isNull();
        assertThat(converter.toOffsetDateTime(" ", new AtomicBoolean())).isNull();
    }

    @Test
    void unparseable_isNullWithOneWarnPerRequest(CapturedOutput output) {
        AtomicBoolean warned = new AtomicBoolean();

        assertThat(converter.toOffsetDateTime("28/10/2026", warned)).isNull();
        assertThat(converter.toOffsetDateTime("yesterday", warned)).isNull();

        assertThat(warned).isTrue();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> output.getOut().contains("Unparseable Open Bee timestamp"));
        assertThat(output.getOut().split("Unparseable Open Bee timestamp", -1)).hasSize(2);
    }
}
