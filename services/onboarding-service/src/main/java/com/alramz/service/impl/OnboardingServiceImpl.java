package com.alramz.service.impl;

import com.alramz.exception.ApplicationException;
import com.alramz.exception.ExternalSystemException;
import com.alramz.exception.TechnicalException;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.OnboardingRequest;
import com.alramz.model.OnboardingResponse;
import com.alramz.repository.DfmOnboardingRepository;
import com.alramz.service.DuplicateCheckService;
import com.alramz.service.OnboardingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OnboardingServiceImpl implements OnboardingService {

    private final DuplicateCheckService duplicateCheckService;
    private final DfmOnboardingRepository dfmOnboardingRepository;

    public OnboardingServiceImpl(DuplicateCheckService duplicateCheckService,
                                 DfmOnboardingRepository dfmOnboardingRepository) {
        this.duplicateCheckService = duplicateCheckService;
        this.dfmOnboardingRepository = dfmOnboardingRepository;
    }

    @Override
    @Loggable
    public OnboardingResponse onboard(OnboardingRequest request) {
        String correlationId = java.util.UUID.randomUUID().toString();
        String memberReferenceNumber = correlationId;

        validateRequiredFields(request);
        validateNationalityPresence(request);
        validateUsCitizen(request);

        String normalizedMobile = normalizeMobile(request.getCustMobile());
        request.setCustMobile(normalizedMobile);

        boolean[] duplicates = duplicateCheckService.checkDuplicates(
                request.getCustNin(),
                request.getEidNo(),
                request.getCustEmail(),
                request.getPpNo()
        );

        logDuplicates(duplicates);

        try {
            dfmOnboardingRepository.insert(request, memberReferenceNumber);
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            log.error("Failed to persist onboarding request", e);
            throw new TechnicalException("Failed to persist onboarding request: " + e.getMessage(), "ONB011");
        }

        OnboardingResponse response = new OnboardingResponse();
        response.setResponseCode("200");
        response.setMemberReferenceNumber(java.util.UUID.fromString(memberReferenceNumber));
        response.setMemberClientId(java.util.UUID.fromString(memberReferenceNumber));
        return response;
    }

    private void validateRequiredFields(OnboardingRequest request) {
        if (isBlank(request.getCustMobile())) {
            throw new ApplicationException("cust_mobile", "Mobile Number (cust_mobile) is missing", "ONB001");
        }
        if (isBlank(request.getCustEmail())) {
            throw new ApplicationException("cust_email", "Email Address (cust_email) is missing", "ONB002");
        }
        if (isBlank(request.getCustNin())) {
            throw new ApplicationException("cust_nin", "NIN Number (cust_nin) is missing", "ONB003");
        }
        if (isBlank(request.getKycMatch())) {
            throw new ApplicationException("kyc_match", "Background check (kyc_match) is missing", "ONB004");
        }
        if (isBlank(request.getFatcaUscitizen())) {
            throw new ApplicationException("fatca_uscitizen", "USCitizen (fatca_uscitizen) is missing", "ONB005");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void validateNationalityPresence(OnboardingRequest request) {
        boolean hasEidNationality = request.getEidNationality() != null && !request.getEidNationality().isBlank();
        boolean hasPassportNationality = request.getPpNationality() != null && !request.getPpNationality().isBlank();
        if (!hasEidNationality && !hasPassportNationality) {
            throw new ApplicationException("nationality", "Client nationality is missing", "ONB006");
        }
    }

    private void validateUsCitizen(OnboardingRequest request) {
        if (isUsCitizenFlag(request.getFatcaUscitizen())) {
            throw new ApplicationException("fatca_uscitizen", "Online onboarding is unavailable for US citizens", "ONB007");
        }
        if (isUsCitizen(request.getEidNationality())) {
            throw new ApplicationException("eid_nationality", "Online onboarding is unavailable for US citizens - eid_nationality", "ONB008");
        }
        if (isUsCitizen(request.getPpNationality())) {
            throw new ApplicationException("pp_nationality", "Online onboarding is unavailable for US citizens - pp_nationality", "ONB009");
        }
        if (isUsCitizen(request.getPinfCountry())) {
            throw new ApplicationException("pinf_country", "Online onboarding is unavailable for US citizens - pinf_country", "ONB010");
        }
    }

    private boolean isUsCitizenFlag(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return "Y".equals(value.trim().toUpperCase());
    }

    private boolean isUsCitizen(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return "USA".equals(value.trim().toUpperCase());
    }

    private String normalizeMobile(String mobile) {
        if (mobile != null && mobile.startsWith("971")) {
            return "00" + mobile.substring(3);
        }
        return mobile;
    }

    private void logDuplicates(boolean[] duplicates) {
        String[] labels = {"NIN", "EmiratesID", "Email", "Passport"};
        for (int i = 0; i < duplicates.length; i++) {
            if (duplicates[i]) {
                log.warn("Duplicate detected for field: {}", labels[i]);
            }
        }
    }
}
