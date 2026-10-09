package com.alramz.service;

import com.alramz.model.OnboardingRequest;
import com.alramz.model.OnboardingResponse;

public interface OnboardingService {

    OnboardingResponse onboard(OnboardingRequest request);
}
