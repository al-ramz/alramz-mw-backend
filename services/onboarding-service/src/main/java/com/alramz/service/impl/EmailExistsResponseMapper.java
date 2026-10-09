package com.alramz.service.impl;

import com.alramz.model.ETradeResponse;
import com.alramz.model.ValidationRequest;
import com.alramz.model.ValidationResponse;
import com.alramz.service.ValidationResponseMapper;
import org.springframework.stereotype.Component;

@Component
public class EmailExistsResponseMapper implements ValidationResponseMapper {

    @Override
    public ValidationResponse map(ETradeResponse externalResponse, ValidationRequest request) {
        boolean valid = "0".equals(externalResponse.errorCode());
        int existsFlag = externalResponse.resData().path("Exists").asInt(-1);
        boolean exists = existsFlag != 0;
        String message = exists ? "Email already exists" : "Email does not exist";

        return new ValidationResponse(
                request.getValidationType().getValue(), // NOPMD LawOfDemeter
                exists,
                valid,
                message,
                request.getReferenceNo()
        );
    }
}
