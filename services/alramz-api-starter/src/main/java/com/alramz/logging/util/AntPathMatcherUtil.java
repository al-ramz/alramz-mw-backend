package com.alramz.logging.util;

import org.springframework.util.AntPathMatcher;

/**
 * Convenience wrapper around Spring's {@link AntPathMatcher} so exclusion
 * patterns can be evaluated without each call site instantiating a matcher.
 * <p>
 * Patterns use Spring Ant-style syntax (e.g. {@code /actuator/**}).
 */
public final class AntPathMatcherUtil {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private AntPathMatcherUtil() {
    }

    public static boolean match(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        try {
            return MATCHER.match(pattern, path);
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            return false;
        }
    }
}
