package com.alramz.exception;

import lombok.Getter;

/** The service's only typed failure; the handler maps its {@link ErrorCode} to a status and body. */
@Getter
public class RiskComplianceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public RiskComplianceException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.code() + ": " + errorCode.message(), cause);
        this.errorCode = errorCode;
    }
}
