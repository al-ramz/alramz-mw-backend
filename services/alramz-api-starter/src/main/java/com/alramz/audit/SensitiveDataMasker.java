package com.alramz.audit;

import com.alramz.logging.util.LogMaskingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.io.IOException;
import java.util.Collections;

public class SensitiveDataMasker {

    private static final Set<String> SENSITIVE_KEYS_SET = buildSensitiveKeysSet();

    private static Set<String> buildSensitiveKeysSet() {
        Set<String> keys = new HashSet<>();
        // Add all keys in lowercase for case-insensitive O(1) lookup
        String[] sensitiveKeys = {
            "client_secret", "api_key", "apikey", "access_token", "accesstoken",
            "authorization", "password", "consumerpassword", "secret", "cookie",
            "eid_attachment_front", "eid_attachment_back", "pinf_signatureimage", "pp_attachment",
            "cust_nin", "eid_no", "passportnumber", "pp_no"
        };
        for (String key : sensitiveKeys) {
            keys.add(key.toLowerCase());
        }
        return Collections.unmodifiableSet(keys);
    }

    private static final Pattern BASE64_LONG_PATTERN = Pattern.compile("^[A-Za-z0-9+/]{40,}={0,2}$");
    private static final int MAX_RECURSION_DEPTH = 20;

    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public SensitiveDataMasker(ObjectMapper objectMapper, boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public Object mask(Object obj) {
        if (!enabled || obj == null) {
            return obj;
        }
        if (obj instanceof Map<?, ?> map) {
            return maskMap(map);
        }
        if (obj instanceof Collection<?> collection) {
            return maskCollection(collection);
        }
        if (obj instanceof String str) {
            return maskString(null, str);
        }
        return obj;
    }

    public String maskRequestBody(HttpServletRequest request) {
        if (!enabled || request == null) {
            return null;
        }
        try {
            ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
            if (wrapper == null || wrapper.getContentAsByteArray().length == 0) {
                return null;
            }
            return maskBody(new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            return null;
        }
    }

    public String maskResponseBody(ContentCachingResponseWrapper response) {
        if (!enabled || response == null) {
            return null;
        }
        try {
            byte[] body = response.getContentAsByteArray();
            if (body.length == 0) {
                return null;
            }
            return maskBody(new String(body, StandardCharsets.UTF_8));
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            return null;
        }
    }

    /** Masks sensitive values inside a raw (usually JSON) HTTP body and truncates it; null when masking is off or the body is empty. */
    public String maskBody(String body) {
        if (!enabled || body == null || body.isEmpty()) {
            return null;
        }
        return LogMaskingUtil.mask(truncate(body, 5000));
    }

    public String maskHeaders(HttpServletRequest request) {
        if (!enabled || request == null) {
            return null;
        }
        try {
            Map<String, String> masked = new LinkedHashMap<>();
            Enumeration<String> names = request.getHeaderNames();
            if (names == null) {
                return null;
            }
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                List<String> values = Collections.list(request.getHeaders(name));
                masked.put(name, LogMaskingUtil.maskHeader(name, values));
            }
            return objectMapper.writeValueAsString(masked);
        } catch (IOException e) {
            return null;
        }
    }

    private Map<String, Object> maskMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String keyStr = entry.getKey() != null ? entry.getKey().toString() : "";
            String keyLower = keyStr.toLowerCase();
            Object value = entry.getValue();

            if (isSensitiveKey(keyLower)) {
                result.put(keyStr, maskSensitiveValue(keyLower, value));
            } else if (value instanceof Map<?, ?> nested) {
                result.put(keyStr, maskMap(nested));
            } else if (value instanceof Collection<?> coll) {
                result.put(keyStr, maskCollection(coll));
            } else if (value instanceof String str) {
                result.put(keyStr, maskString(keyLower, str));
            } else {
                result.put(keyStr, value);
            }
        }
        return result;
    }

    private Collection<Object> maskCollection(Collection<?> collection) {
        List<Object> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                result.add(maskMap(map));
            } else if (item instanceof Collection<?> nested) {
                result.add(maskCollection(nested));
            } else if (item instanceof String str) {
                result.add(maskString(null, str));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private String maskString(String key, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (isBase64Image(key, value)) {
            int len = Math.min(20, value.length());
            return value.substring(0, len) + "... [truncated]";
        }
        if (isSensitiveKey(key) || LogMaskingUtil.isSensitiveKey(key)) {
            return "***";
        }
        if (value.length() > 100 && (value.startsWith("data:image") || value.startsWith("/9j/") || value.startsWith("iVBOR"))) {
            int len = Math.min(20, value.length());
            return value.substring(0, len) + "... [truncated]";
        }
        return value;
    }

    private Object maskSensitiveValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return maskString(key, str);
        }
        return "***";
    }

    private boolean isSensitiveKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        return SENSITIVE_KEYS_SET.contains(key) || LogMaskingUtil.isSensitiveKey(key);
    }

    private boolean isBase64Image(String key, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (key != null && (key.contains("attachment") || key.contains("image") || key.contains("signature"))) {
            return BASE64_LONG_PATTERN.matcher(value).matches();
        }
        return false;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[truncated]";
    }
}
