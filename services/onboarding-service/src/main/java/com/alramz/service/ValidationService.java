package com.alramz.service;

import com.alramz.model.GenericResponse;
import com.alramz.model.ValidationRequest;

public interface ValidationService {
    GenericResponse validate(ValidationRequest request);
}
