package com.alramz.exception;

public class IbanValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public IbanValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
