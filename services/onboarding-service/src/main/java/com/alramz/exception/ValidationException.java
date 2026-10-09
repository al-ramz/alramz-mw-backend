package com.alramz.exception;

public class ValidationException extends RuntimeException {

    private final String field;
    private final String serviceErrorResponseCode;

    public ValidationException(String field, String message) {
        this(field, message, null);
    }

    public ValidationException(String field, String message, String serviceErrorResponseCode) {
        super(message);
        this.field = field;
        this.serviceErrorResponseCode = serviceErrorResponseCode;
    }

    public ValidationException(String message) {
        this(null, message, null);
    }

    public String getField() {
        return field;
    }

    public String getServiceErrorResponseCode() {
        return serviceErrorResponseCode;
    }
}
