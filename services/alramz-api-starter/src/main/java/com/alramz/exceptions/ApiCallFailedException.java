package com.alramz.exceptions;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiCallFailedException extends RuntimeException {

    private final int responseCode;
    private final String responseMessage;
    /** Downstream URI and HTTP method of the failed call; {@code null} when not known. */
    private final String uri;
    private final String method;

    public ApiCallFailedException(String uri, String method, int status, String responseMessage) {
        super(responseMessage);
        this.responseCode = status;
        this.responseMessage = responseMessage;
        this.uri = uri;
        this.method = method;
    }

    public ApiCallFailedException(int status, String responseMessage, Throwable cause) {
        super(responseMessage, cause);
        this.responseCode = status;
        this.responseMessage = responseMessage;
        this.uri = null;
        this.method = null;
    }
}
