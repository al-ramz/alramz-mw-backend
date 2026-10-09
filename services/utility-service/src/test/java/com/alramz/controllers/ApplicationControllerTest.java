package com.alramz.controllers;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationControllerTest {

    @Test
    void info_shouldReportNameHostAndCurrentTime() {
        Instant before = Instant.now();

        var info = new ApplicationController("utility-service", "localhost").info();

        assertThat(info).containsEntry("applicationName", "utility-service")
                .containsEntry("host", "localhost");
        assertThat((Instant) info.get("timestamp")).isBetween(before, Instant.now());
    }
}
