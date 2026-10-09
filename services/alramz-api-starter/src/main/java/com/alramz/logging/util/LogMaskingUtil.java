package com.alramz.logging.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reusable, dependency-free log masking utility.
 * <p>
 * Masks values of well-known sensitive keys (password, token, JWT, credit card
 * numbers, Aadhaar, PAN, SSN, CVV, ...) inside log output and supports custom
 * regex patterns defined by the consumer. The utility is thread-safe after
 * configuration and is safe to call from any filter or logger.
 */
public final class LogMaskingUtil {

    private static final char QUOTE = '"';
    private static final String DEFAULT_MASK = "********";

    private static volatile boolean enabled = true;
    private static volatile String mask = DEFAULT_MASK;
    private static final CopyOnWriteArrayList<String> sensitiveKeys = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Pattern> customPatterns = new CopyOnWriteArrayList<>();

    private static final Pattern JSON_PAIR = Pattern.compile(
            "\"([^\"]*)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|\\d+(?:\\.\\d+)?|true|false|null)");

    private static final List<Pattern> DEFAULT_PATTERNS = List.of(
            Pattern.compile("(?i)(eyJ[A-Za-z0-9_\\-]+\\.eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-.]+)"),
            Pattern.compile("(?i)(bearer\\s+[A-Za-z0-9_=\\-\\.]+)"),
            Pattern.compile("(?i)(authorization\\s*[=:]{1,2}\\s*\\S+)"),
            Pattern.compile("(?i)(api[_-]?key\\s*[=:]{1,2}\\s*\\S+)"),
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
            Pattern.compile("\\b(?:\\d{4}[ -]?){3}\\d{4}\\b"));

    private LogMaskingUtil() {
    }

    public static void configure(boolean enabled, String mask, List<String> keys, List<String> patterns) {
        LogMaskingUtil.enabled = enabled;
        LogMaskingUtil.mask = mask == null || mask.isBlank() ? DEFAULT_MASK : mask;
        sensitiveKeys.clear();
        if (keys != null) {
            sensitiveKeys.addAll(keys);
        }
        customPatterns.clear();
        if (patterns != null) {
            for (String p : patterns) {
                try {
                    customPatterns.add(Pattern.compile(p));
                } catch (PatternSyntaxException e) {
                    // ignore invalid pattern
                }
            }
        }
    }

    /**
     * Mask any sensitive content found in arbitrary text (JSON bodies, log
     * messages, header values, ...). Returns the original value when masking is
     * disabled or the input is {@code null}.
     */
    public static String mask(String text) {
        if (!enabled || text == null || text.isEmpty()) {
            return text;
        }
        String masked = maskJsonKeys(text);
        for (Pattern pattern : customPatterns) {
            masked = pattern.matcher(masked).replaceAll(mask);
        }
        for (Pattern pattern : DEFAULT_PATTERNS) {
            masked = pattern.matcher(masked).replaceAll(mask);
        }
        return masked;
    }

    private static String maskJsonKeys(String text) {
        Matcher matcher = JSON_PAIR.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
             if (isSensitiveKey(key)) {
                 matcher.appendReplacement(sb, Matcher.quoteReplacement(QUOTE + key + QUOTE + ": " + QUOTE + mask + QUOTE));
             } else {
                 matcher.appendReplacement(sb, Matcher.quoteReplacement(QUOTE + key + QUOTE + ": " + value));
             }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Mask a single value associated with the given key.
     */
    public static String maskValue(String key, String value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return mask;
        }
        return value;
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lowered = key.toLowerCase();
        for (String sensitive : sensitiveKeys) {
            if (lowered.contains(sensitive.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static String maskHeader(String headerName, List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (isSensitiveKey(headerName)) {
            return mask;
        }
        return String.join(",", values);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static List<String> getSensitiveKeys() {
        return Collections.unmodifiableList(sensitiveKeys);
    }

    public static void setDefaults(List<String> keys, String maskReplacement) {
        configure(true, maskReplacement, keys, List.of());
    }
}
