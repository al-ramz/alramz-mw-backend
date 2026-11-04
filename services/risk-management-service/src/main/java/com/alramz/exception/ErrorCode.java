package com.alramz.exception;

import org.springframework.http.HttpStatus;

/** Every errorCode the GET routes return in {@code GenericResponse}: CLS = countries, CRT = ratings. */
public enum ErrorCode {

    CLS001(HttpStatus.BAD_GATEWAY, ErrorCode.PROVIDER_ERROR),
    CLS002(HttpStatus.BAD_GATEWAY, ErrorCode.PROVIDER_INVALID),
    CLS003(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PROVIDER_UNAVAILABLE),
    CLS004(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR),
    CRT001(HttpStatus.BAD_GATEWAY, ErrorCode.PROVIDER_ERROR),
    CRT002(HttpStatus.BAD_GATEWAY, ErrorCode.PROVIDER_INVALID),
    CRT003(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PROVIDER_UNAVAILABLE),
    CRT004(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);

    private static final String PROVIDER_ERROR = "Country data provider returned an error";
    private static final String PROVIDER_INVALID = "Country data provider returned an invalid response";
    private static final String PROVIDER_UNAVAILABLE = "Country data provider unavailable";
    private static final String INTERNAL_ERROR = "Internal Server Error";

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public String code() {
        return name();
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    /** The vendor answered with a non-2xx status (legacy routes report this as 1018). */
    public boolean isVendorRejection() {
        return this == CLS001 || this == CRT001;
    }
}
