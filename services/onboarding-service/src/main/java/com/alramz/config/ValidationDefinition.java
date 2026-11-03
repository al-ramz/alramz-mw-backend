package com.alramz.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ValidationDefinition(
    String endpoint,
    String method,
    Map<String, String> requestMapping
) {

    // Compact-ctor reassignment is how a record stores the defensive copy (PMD false positive); null is kept as-is.
    @SuppressWarnings({"PMD.UnusedAssignment", "PMD.NullAssignment"})
    public ValidationDefinition {
        requestMapping = requestMapping == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(requestMapping));
    }
}
