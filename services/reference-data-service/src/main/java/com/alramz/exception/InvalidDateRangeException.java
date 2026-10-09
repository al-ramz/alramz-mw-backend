package com.alramz.exception;

/**
 * Thrown when a non-blank {@code startDate}/{@code endDate} on the commissions report
 * request matches the ISO-8601 pattern but is not a valid calendar date
 * (e.g. {@code "2024-13-45"}).
 */
public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException(String message) {
        super(message);
    }
}
