package com.alramz.service;

import com.alramz.config.ValidationDefinition;
import com.alramz.model.ValidationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(result)
                .isNotNull()
                .containsEntry("emailAddress", "test@example.com")
                .containsEntry("Reference_No", "1");
    }
}
