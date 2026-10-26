package com.alramz.exception;

/** The requested MMF resource does not exist in the selected book; always answered with 404. */
public class MmfNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public MmfNotFoundException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
