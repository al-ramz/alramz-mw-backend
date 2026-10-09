package com.alramz.service;

import com.alramz.model.GenericResponse;
import com.alramz.model.IBANRequest;

public interface IBANValidationService {
    GenericResponse validate(IBANRequest request);
}
