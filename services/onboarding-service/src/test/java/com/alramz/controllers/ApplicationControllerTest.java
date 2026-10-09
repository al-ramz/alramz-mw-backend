package com.alramz.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    @Mock
    private Environment environment;

    @Test
    void info_shouldReturnApplicationInfo() throws Exception {
        ApplicationController controller = new ApplicationController();

        Field applicationNameField = ApplicationController.class.getDeclaredField("applicationName");
        applicationNameField.setAccessible(true);
        applicationNameField.set(controller, "test-service");

        Field hostField = ApplicationController.class.getDeclaredField("host");
        hostField.setAccessible(true);
        hostField.set(controller, "localhost");

        Map<String, Object> response = controller.info();

        assertThat(response).isNotNull();
        assertThat(response.get("applicationName")).isEqualTo("test-service");
        assertThat(response.get("host")).isEqualTo("localhost");
        assertThat(response.get("timestamp")).isNotNull();
    }
}
