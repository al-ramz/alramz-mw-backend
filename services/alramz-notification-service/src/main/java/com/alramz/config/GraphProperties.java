package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.graph")
public record GraphProperties(
    String tenantId,
    String clientId,
    String clientSecret,
    String graphScopes
) {}
