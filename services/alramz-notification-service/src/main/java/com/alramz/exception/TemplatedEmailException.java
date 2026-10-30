package com.alramz.exception;

/**
 * A templated-email failure carrying an EML0xx code; GlobalExceptionHandler picks the HTTP status.
 * No cause constructor: gateway exceptions are mapped by code, never chained.
 */
@SuppressWarnings("PMD.MissingSerialVersionUID")
public class TemplatedEmailException extends RuntimeException {

    private final String code;

    public TemplatedEmailException(final String code, final String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
