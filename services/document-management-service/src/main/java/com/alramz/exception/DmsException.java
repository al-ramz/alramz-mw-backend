package com.alramz.exception;

import lombok.Getter;

/**
 * Every business and client-facing DMS failure. The HTTP status comes from {@link ErrorCode#status()}; the
 * message is client-safe (metadata IDs and names only, never values).
 */
@Getter
public class DmsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public DmsException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public DmsException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DmsException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
