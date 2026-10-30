package com.alramz.controllers;

import com.alramz.api.DfmInvestorsApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.DfmInvestorRegistrationRequest;
import com.alramz.model.GenericResponse;
import com.alramz.service.DfmInvestorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Errors are shaped by DfmInvestorExceptionHandler; correlationId is filled by the starter's response advice. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DfmInvestorController implements DfmInvestorsApi {

    private final DfmInvestorService dfmInvestorService;

    @Override
    @JwtSecured(roles = "APP_DATA_VALIDATION")
    @Loggable(logArgs = false)
    public ResponseEntity<GenericResponse> registerDfmInvestor(DfmInvestorRegistrationRequest dfmInvestorRegistrationRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new GenericResponse("201", "Created", dfmInvestorService.register(dfmInvestorRegistrationRequest)));
    }
}
