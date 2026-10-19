package com.alramz.exception;

/**
 * Thrown when the {@code brok} Oracle datasource is unreachable, or any other
 * SQL/JDBC failure occurs while looking up relationship manager or commission data.
 */
public class RelationshipManagerLookupException extends RuntimeException {

    public RelationshipManagerLookupException(String message) {
        super(message);
    }
}
