package com.alramz.exception;

/** Finoux could not be reached in time; always answered with 503 / FNX012. */
public class FinouxUnavailableException extends RuntimeException {

    public FinouxUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
