package com.alramz.controllers;

import com.alramz.api.RiskComplianceApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.CountryListResponse;
import com.alramz.model.CountryRatingResponse;
import com.alramz.model.GenericResponse;
import com.alramz.service.RiskComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** New-contract GET routes. Failures propagate as RiskComplianceException to GlobalExceptionHandler. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RiskComplianceController implements RiskComplianceApi {

    private final RiskComplianceService riskComplianceService;

    @Override
    @JwtSecured(roles = "APP_RISK_MANAGEMENT")
    @Loggable
    public ResponseEntity<GenericResponse> getCountries() {
        return okResponse(new CountryListResponse().countryList(riskComplianceService.getCountries()));
    }

    @Override
    @JwtSecured(roles = "APP_RISK_MANAGEMENT")
    @Loggable
    public ResponseEntity<GenericResponse> getCountryRatings() {
        return okResponse(new CountryRatingResponse().countryRating(riskComplianceService.getCountryRatings()));
    }

    private static ResponseEntity<GenericResponse> okResponse(Object payload) {
        GenericResponse body = new GenericResponse();
        body.setResponseCode("200");
        body.setResponseMessage("OK");
        body.setResponse(payload);
        body.setCorrelationId(MDCUtil.getCorrelationIdAsUuid());
        return ResponseEntity.ok(body);
    }
}
