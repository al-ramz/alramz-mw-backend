package com.alramz.exception;

public class ExternalSystemException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExternalSystemException(String message) {
        super(message);
    }

    public ExternalSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
