package com.alramz.exceptions;


public class InvalidHttpRequestException extends RuntimeException {
    public InvalidHttpRequestException() {
        super("Invalid request");
    }

    public InvalidHttpRequestException(String message) {
        super(message);
    }

    public InvalidHttpRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidHttpRequestException(Throwable cause) {
        super(cause);
    }

}