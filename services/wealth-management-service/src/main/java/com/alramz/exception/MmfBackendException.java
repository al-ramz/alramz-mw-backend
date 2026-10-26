package com.alramz.exception;

/**
 * A backend or configuration fault; always answered with 500. A null errorCode means "use the
 * operation's own 500 code", which the handler resolves from the request's operationId.
 */
public class MmfBackendException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public MmfBackendException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
