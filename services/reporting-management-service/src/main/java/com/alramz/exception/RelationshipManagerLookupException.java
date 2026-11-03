package com.alramz.exception;

/**
 * Thrown when the {@code brok} Oracle datasource is unreachable, or any other
 * SQL/JDBC failure occurs while looking up relationship manager or commission data.
 */
public class RelationshipManagerLookupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RelationshipManagerLookupException(String message) {
        super(message);
    }

    public RelationshipManagerLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
