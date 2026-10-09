package com.alramz.exception;

@SuppressWarnings("PMD.MissingSerialVersionUID")
public class EmailServiceException extends RuntimeException {

    private final int statusCode;

    public EmailServiceException(final int statusCode, final String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public EmailServiceException(final int statusCode, final String message, final Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
