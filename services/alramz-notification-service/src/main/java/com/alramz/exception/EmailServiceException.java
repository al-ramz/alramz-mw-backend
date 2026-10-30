package com.alramz.exception;

/**
 * A backend (Graph-side or unexpected) email failure. Carries an MGE-B-00x code; the HTTP status is chosen
 * by GlobalExceptionHandler. retryAfter is the Graph Retry-After value in seconds (MGE-B-002 only), else null.
 */
@SuppressWarnings("PMD.MissingSerialVersionUID")
public class EmailServiceException extends RuntimeException {

    private final String code;
    private final String retryAfter;

    public EmailServiceException(final String code, final String message) {
        this(code, message, null, null);
    }

    public EmailServiceException(final String code, final String message, final Throwable cause, final String retryAfter) {
        super(message, cause);
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public String getCode() {
        return code;
    }

    public String getRetryAfter() {
        return retryAfter;
    }
}
