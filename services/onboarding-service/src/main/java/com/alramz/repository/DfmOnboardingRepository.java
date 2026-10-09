package com.alramz.repository;

import com.alramz.model.OnboardingRequest;

public interface DfmOnboardingRepository {

    void insert(OnboardingRequest request, String memberReferenceNumber);
}
