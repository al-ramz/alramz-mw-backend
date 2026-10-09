package com.alramz.service;

import com.alramz.config.ValidationDefinition;
import com.alramz.model.ValidationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;

@Component
public class ValidationRequestMapper {

    private final ObjectMapper objectMapper;

    public ValidationRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> map(ValidationRequest request, ValidationDefinition definition) {
        Map<String, Object> result = new HashMap<>();
        definition.requestMapping().forEach((externalField, internalField) -> {
            Object value = readProperty(request, internalField);
            result.put(externalField, value);
        });
        return result;
    }

    private Object readProperty(ValidationRequest source, String fieldName) {
        try {
            Map<String, Object> map = objectMapper.convertValue(source, Map.class);
            return map.get(fieldName);
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            return null;
        }
    }
}
