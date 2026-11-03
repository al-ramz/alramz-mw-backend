package com.alramz.exception;

/**
 * Thrown when a non-blank {@code startDate}/{@code endDate} on the commissions report
 * request matches the ISO-8601 pattern but is not a valid calendar date
 * (e.g. {@code "2024-13-45"}).
 */
public class InvalidDateRangeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidDateRangeException(String message) {
        super(message);
    }

    public InvalidDateRangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
