package com.alramz.exception;

/** A book could not be reached and nothing was done; always answered with 503. */
public class MmfBackendUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public MmfBackendUnavailableException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
