package com.alramz.exception;

public class TechnicalException extends RuntimeException {

    private final String errorCode;

    public TechnicalException(String message) {
        this(message, "ONB011");
    }

    public TechnicalException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
