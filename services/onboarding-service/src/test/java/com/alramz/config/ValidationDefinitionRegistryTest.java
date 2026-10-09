package com.alramz.config;

import com.alramz.model.ValidationRequest.ValidationTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationDefinitionRegistryTest {

    @Test
    void get_shouldReturnDefinitionForKnownType() {
        ETradeProperties properties = new ETradeProperties(
                "https://example.com", "client", "secret", null, "30", 3000, "/token",
                Map.of("EMAIL_EXISTS", new ETradeProperties.EndpointConfig("/path", "POST", Map.of()))
        );
        ValidationDefinitionRegistry registry = new ValidationDefinitionRegistry(properties);

        ValidationDefinition definition = registry.get(ValidationTypeEnum.EMAIL_EXISTS);
        assertThat(definition).isNotNull();
        assertThat(definition.endpoint()).isEqualTo("/path");
        assertThat(definition.method()).isEqualTo("POST");
    }

    @Test
    void get_shouldThrowForUnknownType() {
        ETradeProperties properties = new ETradeProperties(
                "https://example.com", "client", "secret", null, "30", 3000, "/token",
                Map.of()
        );
        ValidationDefinitionRegistry registry = new ValidationDefinitionRegistry(properties);

        assertThatThrownBy(() -> registry.get(ValidationTypeEnum.EMAIL_EXISTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No validation definition found for type");
    }
}
