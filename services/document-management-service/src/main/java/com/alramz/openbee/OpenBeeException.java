package com.alramz.openbee;

import lombok.Getter;

import java.util.List;

/**
 * Internal, typed failure of an Open Bee call. Always converted by {@link OpenBeeErrorMapper} before it leaves
 * the service layer. The message carries only the kind, status, Open Bee error codes and the endpoint path
 * template: never Open Bee's message text, a URL with values, or a key.
 */
@Getter
public class OpenBeeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Kind { HTTP_ERROR, TIMEOUT, CONNECTION, CIRCUIT_OPEN, BAD_RESPONSE }

    private final Kind kind;
    /** HTTP status of Open Bee's response; 0 when no response was received. */
    private final int httpStatus;
    private final transient List<String> obCodes;
    private final String endpoint;

    public OpenBeeException(Kind kind, int httpStatus, List<String> obCodes, String endpoint) {
        this(kind, httpStatus, obCodes, endpoint, null);
    }

    public OpenBeeException(Kind kind, int httpStatus, List<String> obCodes, String endpoint, Throwable cause) {
        super("Open Bee " + kind + " status=" + httpStatus + " codes=" + (obCodes == null ? List.of() : obCodes)
                + " endpoint=" + endpoint, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.obCodes = obCodes == null ? List.of() : List.copyOf(obCodes);
        this.endpoint = endpoint;
    }

    /** Open Bee is unavailable or failing on its side: timeouts, connection errors, open circuit, 5xx. */
    public boolean isUnavailable() {
        return kind == Kind.TIMEOUT || kind == Kind.CONNECTION || kind == Kind.CIRCUIT_OPEN
                || kind == Kind.HTTP_ERROR && httpStatus >= 500;
    }

    /** Failures worth retrying (read calls) and recording in the circuit breaker: never 4xx or bad bodies. */
    public boolean isTransient() {
        return kind == Kind.TIMEOUT || kind == Kind.CONNECTION || kind == Kind.HTTP_ERROR && httpStatus >= 500;
    }

    public boolean hasCode(String code) {
        return obCodes.contains(code);
    }
}
