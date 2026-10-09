package com.alramz.service;

import com.alramz.model.GenericResponse;
import com.alramz.model.PhoneRequest;

public interface PhoneValidationService {
    GenericResponse validate(PhoneRequest request);
}