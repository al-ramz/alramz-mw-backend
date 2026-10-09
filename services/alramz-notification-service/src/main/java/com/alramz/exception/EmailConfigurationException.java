package com.alramz.exception;

@SuppressWarnings("PMD.MissingSerialVersionUID")
public class EmailConfigurationException extends RuntimeException {

    public EmailConfigurationException(final String message) {
        super(message);
    }

    public EmailConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
