package com.alramz.exception;

@SuppressWarnings("PMD.MissingSerialVersionUID")
public class EmailValidationException extends RuntimeException {

    private final String code;

    public EmailValidationException(final String code, final String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
