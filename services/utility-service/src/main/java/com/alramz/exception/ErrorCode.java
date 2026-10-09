package com.alramz.exception;

import java.util.Arrays;

/** Every errorCode this service returns in {@code GenericResponse}. */
public enum ErrorCode {

    POSTED_USER_ID_INVALID("FNX001", "postedUserId"),
    USERNAME_INVALID("FNX002", "username"),
    POSTED_BY_INVALID("FNX003", "postedBy"),
    POST_DESC_INVALID("FNX004", "postDesc"),
    TAG_SYMBOL_INVALID("FNX005", "tagSymbol"),
    FINOUX_ERROR("FNX010", null),
    FINOUX_REJECTED("FNX011", null),
    FINOUX_UNAVAILABLE("FNX012", null),
    INTERNAL_ERROR("FNX013", null),
    INVALID_REQUEST("FNX090", null);

    private final String code;
    private final String field;

    ErrorCode(String code, String field) {
        this.code = code;
        this.field = field;
    }

    public String code() {
        return code;
    }

    /** The code for a failed request field (nested paths such as {@code post.username} use the last segment). */
    public static ErrorCode forField(String fieldPath) {
        if (fieldPath == null) {
            return INVALID_REQUEST;
        }
        String field = fieldPath.substring(fieldPath.lastIndexOf('.') + 1);
        return Arrays.stream(values())
                .filter(code -> field.equals(code.field))
                .findFirst()
                .orElse(INVALID_REQUEST);
    }
}
