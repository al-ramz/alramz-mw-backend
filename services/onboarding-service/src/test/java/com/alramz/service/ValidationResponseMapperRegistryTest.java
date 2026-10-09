package com.alramz.service;

import com.alramz.config.ValidationDefinition;
import com.alramz.model.ValidationRequest;
import com.alramz.model.ValidationRequest.ValidationTypeEnum;
import com.alramz.service.impl.EIDExistsResponseMapper;
import com.alramz.service.impl.EmailExistsResponseMapper;
import com.alramz.service.impl.MobileExistsResponseMapper;
import com.alramz.service.impl.NinExistsResponseMapper;
import com.alramz.service.impl.PassportExistsResponseMapper;
import com.alramz.service.impl.TPUUIDExistsResponseMapper;
import com.alramz.service.impl.UsernameExistsResponseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ValidationResponseMapperRegistryTest {

    @Test
    void get_shouldReturnMapperForAllKnownTypes() {
        ValidationResponseMapperRegistry registry = new ValidationResponseMapperRegistry(
                new EmailExistsResponseMapper(),
                new PassportExistsResponseMapper(),
                new NinExistsResponseMapper(),
                new UsernameExistsResponseMapper(),
                new EIDExistsResponseMapper(),
                new TPUUIDExistsResponseMapper(),
                new MobileExistsResponseMapper()
        );

        for (ValidationTypeEnum type : ValidationTypeEnum.values()) {
            ValidationResponseMapper mapper = registry.get(type);
            assertThat(mapper).isNotNull();
        }
    }
}
