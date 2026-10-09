package com.alramz.config;

import com.alramz.model.ValidationRequest.ValidationTypeEnum;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ValidationDefinitionRegistry {

    private final Map<ValidationTypeEnum, ValidationDefinition> definitions;

    public ValidationDefinitionRegistry(ETradeProperties etradeProperties) {
        this.definitions = etradeProperties.validations().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> ValidationTypeEnum.fromValue(entry.getKey()),
                        entry -> {
                            ETradeProperties.EndpointConfig config = entry.getValue();
                            return new ValidationDefinition(config.path(), config.method(), config.requestMapping());
                        }
                ));
    }

    public ValidationDefinition get(ValidationTypeEnum type) {
        ValidationDefinition definition = definitions.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("No validation definition found for type: " + type);
        }
        return definition;
    }
}
