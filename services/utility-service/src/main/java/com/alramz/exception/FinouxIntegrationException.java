package com.alramz.exception;

/** Finoux answered, but with an error status or a rejection; always answered with 500. */
public class FinouxIntegrationException extends RuntimeException {

    private final ErrorCode errorCode;

    public FinouxIntegrationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
