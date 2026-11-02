package com.alramz.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationControllerTest {

    @Test
    void info_shouldReturnConfiguredNameHostAndTimestamp() throws Exception {
        ApplicationController controller = new ApplicationController();
        ReflectionTestUtils.setField(controller, "applicationName", "reference-data-service");
        ReflectionTestUtils.setField(controller, "host", "localhost");

        Map<String, Object> info = controller.info();

        assertThat(info)
                .containsEntry("applicationName", "reference-data-service")
                .containsEntry("host", "localhost");
        assertThat(info.get("timestamp")).isInstanceOf(Instant.class);
    }
}
