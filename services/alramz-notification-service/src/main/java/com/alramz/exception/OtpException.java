package com.alramz.exception;

/** An OTP failure carrying an OTP0xx code; GlobalExceptionHandler picks the HTTP status. */
@SuppressWarnings("PMD.MissingSerialVersionUID")
public class OtpException extends RuntimeException {

    private final String code;

    public OtpException(final String code, final String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
