package com.alramz.exception;

public class ApplicationException extends RuntimeException {

    private final String field;
    private final String serviceErrorResponseCode;

    public ApplicationException(String field, String message) {
        this(field, message, null);
    }

    public ApplicationException(String field, String message, String serviceErrorResponseCode) {
        super(message);
        this.field = field;
        this.serviceErrorResponseCode = serviceErrorResponseCode;
    }

    public ApplicationException(String message) {
        this(null, message, null);
    }

    public String getField() {
        return field;
    }

    public String getServiceErrorResponseCode() {
        return serviceErrorResponseCode;
    }
}
