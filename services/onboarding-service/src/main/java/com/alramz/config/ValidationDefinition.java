package com.alramz.config;

import java.util.Map;

public record ValidationDefinition(
    String endpoint,
    String method,
    Map<String, String> requestMapping
) {}
