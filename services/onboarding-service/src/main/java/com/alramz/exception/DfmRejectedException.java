package com.alramz.exception;

import lombok.Getter;

/** DFM refused the registration (DFN006) or still has the same requestId in progress (DFN007). */
@Getter
public class DfmRejectedException extends RuntimeException {

    private final DfmInvestorErrorCode code;

    public DfmRejectedException(DfmInvestorErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
