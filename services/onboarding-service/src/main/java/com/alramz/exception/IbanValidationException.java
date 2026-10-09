package com.alramz.exception;

public class IbanValidationException extends RuntimeException {

    private final String code;

    public IbanValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
