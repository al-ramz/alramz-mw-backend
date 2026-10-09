package com.alramz.service;

import com.alramz.model.ETradeResponse;
import com.alramz.model.ValidationRequest;
import com.alramz.model.ValidationResponse;

public interface ValidationResponseMapper {
    ValidationResponse map(ETradeResponse externalResponse, ValidationRequest request);
}
