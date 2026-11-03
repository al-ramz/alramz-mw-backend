package com.alramz.exception;

public class TechnicalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public TechnicalException(String message) {
        this(message, "ONB011");
    }

    public TechnicalException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public TechnicalException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
