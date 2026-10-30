package com.alramz.service;

import com.alramz.model.DfmInvestorRegistration;
import com.alramz.model.DfmInvestorRegistrationRequest;

public interface DfmInvestorService {

    /**
     * Validates the cross-field rules and registers the investor with DFM.
     *
     * @throws com.alramz.exception.ApplicationException invalid input (DFN001-DFN005)
     * @throws com.alramz.exception.DfmRejectedException DFM rejected it (DFN006/DFN007)
     * @throws com.alramz.exception.DfmUnavailableException DFM failed technically (DFN010/DFN011/DFN014)
     * @throws com.alramz.exception.DfmConfigurationException the DFM adapter is not configured (DFN012)
     */
    DfmInvestorRegistration register(DfmInvestorRegistrationRequest request);
}
