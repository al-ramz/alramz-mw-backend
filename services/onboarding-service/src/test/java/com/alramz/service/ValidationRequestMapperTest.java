package com.alramz.service;

import com.alramz.config.ValidationDefinition;
import com.alramz.model.ValidationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationRequestMapperTest {

    @Test
    void map_shouldMapFieldsCorrectly() {
        ValidationRequestMapper mapper = new ValidationRequestMapper(new com.fasterxml.jackson.databind.ObjectMapper());

        ValidationRequest request = new ValidationRequest()
                .validationType(ValidationRequest.ValidationTypeEnum.EMAIL_EXISTS)
                .referenceNo("1")
                .email("test@example.com");

        ValidationDefinition definition = new ValidationDefinition(
                "/path",
                "POST",
                Map.of("emailAddress", "email", "Reference_No", "referenceNo")
        );

        Map<String, Object> result = mapper.map(request, definition);

        assertThat(result).isNotNull();
        assertThat(result.get("emailAddress")).isEqualTo("test@example.com");
        assertThat(result.get("Reference_No")).isEqualTo("1");
    }
}
