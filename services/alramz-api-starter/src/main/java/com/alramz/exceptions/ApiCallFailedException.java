package com.alramz.exceptions;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiCallFailedException extends RuntimeException {

    private final int responseCode;
    private final String responseMessage;
    public ApiCallFailedException(String uri, String method, int status, String responseMessage) {
        super(responseMessage);
        this.responseCode = status;
        this.responseMessage = responseMessage;
    }

    public ApiCallFailedException(int status, String responseMessage, Throwable cause) {
        super(responseMessage, cause);
        this.responseCode = status;
        this.responseMessage = responseMessage;
    }
}