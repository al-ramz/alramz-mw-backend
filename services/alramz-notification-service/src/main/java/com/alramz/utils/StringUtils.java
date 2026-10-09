package com.alramz.utils;

public final class StringUtils {

    private StringUtils() {
    }

    public static String requireNonBlank(String value, String property) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("graph." + property + " is required but not set");
        }
        return value;
    }
}
