package com.alramz.exception;

import lombok.Getter;

/** DFM failed technically: bad response (DFN010), timeout (DFN011) or open circuit (DFN014). */
@Getter
public class DfmUnavailableException extends RuntimeException {

    private final DfmInvestorErrorCode code;

    public DfmUnavailableException(DfmInvestorErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
