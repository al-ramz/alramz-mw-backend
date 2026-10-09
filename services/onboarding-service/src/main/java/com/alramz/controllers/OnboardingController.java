package com.alramz.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alramz.api.DfmOnboardingApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.model.OnboardingRequest;
import com.alramz.model.OnboardingResponse;
import com.alramz.service.OnboardingService;

import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping ("/api/v1")
@RequiredArgsConstructor 
public class OnboardingController implements  DfmOnboardingApi {

    private final OnboardingService onboardingService;
    
    @Override
    @JwtSecured(roles = "APP_DATA_VALIDATION")
    public ResponseEntity<OnboardingResponse> onboard(OnboardingRequest onboardingRequest) {
        return ResponseEntity.ok(onboardingService.onboard(onboardingRequest));
    }
}
