package com.alramz.exception;

/**
 * An SMS failure carrying an SMS0xx code; GlobalExceptionHandler picks the HTTP status.
 * Deliberately no cause constructor: provider exceptions embed credential-bearing URLs and are never chained.
 */
@SuppressWarnings("PMD.MissingSerialVersionUID")
public class SmsException extends RuntimeException {

    private final String code;

    public SmsException(final String code, final String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
