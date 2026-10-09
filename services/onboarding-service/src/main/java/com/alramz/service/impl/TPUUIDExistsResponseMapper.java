package com.alramz.service.impl;

import com.alramz.model.ETradeResponse;
import com.alramz.model.ValidationRequest;
import com.alramz.model.ValidationResponse;
import com.alramz.service.ValidationResponseMapper;
import org.springframework.stereotype.Component;

@Component
public class TPUUIDExistsResponseMapper implements ValidationResponseMapper {

    @Override
    public ValidationResponse map(ETradeResponse externalResponse, ValidationRequest request) {
        boolean valid = "0".equals(externalResponse.errorCode());
        boolean exists;
        if (externalResponse.exists() != null) {
            exists = externalResponse.exists();
        } else if (externalResponse.resData() != null) {
            int existsFlag = externalResponse.resData().path("Exists").asInt(-1);
            exists = existsFlag != 0;
        } else {
            exists = false;
        }
        String message = exists ? "Third-party UUID already exists" : "Third-party UUID does not exist";

        return new ValidationResponse(
                request.getValidationType().getValue(), // NOPMD LawOfDemeter
                exists,
                valid,
                message,
                request.getReferenceNo()
        );
    }
}
