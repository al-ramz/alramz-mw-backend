package com.alramz.logging.util;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.constants.LoggingConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.*;

public final class MethodExecutionLoggingHelper {

    private static final Logger logger = LoggerFactory.getLogger(MethodExecutionLoggingHelper.class);
    private static final String DEFAULT_SEPARATOR = "================================================================================";

    private final LoggingProperties properties;
    private final ObjectMapper objectMapper;

    public MethodExecutionLoggingHelper(LoggingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String getSeparator() {
        String sep = properties.getAspect().getSeparator();
        if (sep != null && !sep.isBlank()) {
            sep = sep.replace("\\r\\n", "\r\n")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r");
        }
        return sep != null && !sep.isBlank() ? sep : DEFAULT_SEPARATOR;
    }

    public boolean isLogEntry() {
        return properties.getAspect().isLogEntry();
    }

    public boolean isLogExit() {
        return properties.getAspect().isLogExit();
    }

    public boolean isLogArgs() {
        return properties.getAspect().isLogArgs();
    }

    public boolean isLogReturn() {
        return properties.getAspect().isLogReturn();
    }

    public boolean isLogExceptions() {
        return properties.getAspect().isLogExceptions();
    }

    public int getMaxPayloadLength() {
        return properties.getAspect().getMaxPayloadLength();
    }

    public boolean isExcluded(Class<?> targetClass) {
        if (targetClass == null) {
            return false;
        }
        String className = targetClass.getName();
        List<String> excludedPackages = properties.getAspect().getExcludedPackages();
        if (excludedPackages != null) {
            for (String pkg : excludedPackages) {
                if (pkg != null && !pkg.isBlank() && className.startsWith(pkg)) {
                    return true;
                }
            }
        }
        List<String> excludedClassPatterns = properties.getAspect().getExcludedClassPatterns();
        if (excludedClassPatterns != null) {
            for (String pattern : excludedClassPatterns) {
                if (pattern != null && !pattern.isBlank() && className.contains(pattern)) {
                    return true;
                }
            }
        }
        if (targetClass == Object.class || targetClass.isInterface() || targetClass.isPrimitive()) {
            return true;
        }
        return false;
    }

    public String serialize(Object value, int maxLength) {
        if (value == null) {
            return "null";
        }
        String text;
        if (value instanceof String s) {
            text = s;
        } else if (value instanceof Number || value instanceof Boolean) {
            text = String.valueOf(value);
        } else if (value.getClass().isArray()) {
            text = serializeArray(value, maxLength);
        } else if (value instanceof Collection<?> collection) {
            text = serializeCollection(collection, maxLength);
        } else if (value instanceof Map<?, ?> map) {
            text = serializeMap(map, maxLength);
        } else {
            text = serializeObject(value, maxLength);
        }
        return truncate(mask(text), maxLength);
    }

    private String serializeArray(Object array, int maxLength) {
        int len = Array.getLength(array);
        List<Object> items = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            items.add(Array.get(array, i));
        }
        return serializeCollection(items, maxLength);
    }

    private String serializeCollection(Collection<?> collection, int maxLength) {
        List<String> parts = new ArrayList<>(collection.size());
        for (Object item : collection) {
            parts.add(serialize(item, maxLength));
        }
        String text = parts.toString();
        return truncate(text, maxLength);
    }

    private String serializeMap(Map<?, ?> map, int maxLength) {
        Map<String, String> parts = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String value = serialize(entry.getValue(), maxLength);
            parts.put(key, value);
        }
        String text = parts.toString();
        return truncate(text, maxLength);
    }

    private String serializeObject(Object value, int maxLength) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return truncate(json, maxLength);
        } catch (JsonProcessingException e) {
            logger.debug("Failed to serialize argument/return value of type {}: {}", value.getClass().getName(), e.getMessage());
            return value.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(value));
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...[truncated]";
    }

    private String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return LogMaskingUtil.mask(text);
    }

    public String formatArgs(ProceedingJoinPoint joinPoint, int maxLength) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "[]";
        }
        List<String> parts = new ArrayList<>(args.length);
        for (Object arg : args) {
            parts.add(serialize(arg, maxLength));
        }
        return parts.toString();
    }
}
