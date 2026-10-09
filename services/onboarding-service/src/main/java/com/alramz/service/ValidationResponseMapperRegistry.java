package com.alramz.service;

import com.alramz.model.ValidationRequest.ValidationTypeEnum;
import com.alramz.service.impl.EIDExistsResponseMapper;
import com.alramz.service.impl.EmailExistsResponseMapper;
import com.alramz.service.impl.MobileExistsResponseMapper;
import com.alramz.service.impl.NinExistsResponseMapper;
import com.alramz.service.impl.PassportExistsResponseMapper;
import com.alramz.service.impl.TPUUIDExistsResponseMapper;
import com.alramz.service.impl.UsernameExistsResponseMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ValidationResponseMapperRegistry {

    private final Map<ValidationTypeEnum, ValidationResponseMapper> mappers;

    public ValidationResponseMapperRegistry(EmailExistsResponseMapper emailMapper,
                                            PassportExistsResponseMapper passportMapper,
                                            NinExistsResponseMapper ninMapper,
                                            UsernameExistsResponseMapper usernameMapper,
                                            EIDExistsResponseMapper eidMapper,
                                            TPUUIDExistsResponseMapper tpUuidMapper,
                                            MobileExistsResponseMapper mobileMapper) {
        this.mappers = Map.of(
                ValidationTypeEnum.EMAIL_EXISTS, emailMapper,
                ValidationTypeEnum.PASSPORT_EXISTS, passportMapper,
                ValidationTypeEnum.NIN_EXISTS, ninMapper,
                ValidationTypeEnum.USERNAME_EXISTS, usernameMapper,
                ValidationTypeEnum.EID_EXISTS, eidMapper,
                ValidationTypeEnum.TP_UUID_EXISTS, tpUuidMapper,
                ValidationTypeEnum.MOBILE_EXISTS, mobileMapper
        );
    }

    public ValidationResponseMapper get(ValidationTypeEnum type) {
        ValidationResponseMapper mapper = mappers.get(type);
        if (mapper == null) {
            throw new IllegalArgumentException("No response mapper found for type: " + type);
        }
        return mapper;
    }
}
