package com.alramz.exception;

/** The request conflicts with the current state of a book (MMF033, MMF044); always answered with 409. */
public class MmfConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public MmfConflictException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
