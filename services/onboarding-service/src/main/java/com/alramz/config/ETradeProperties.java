package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "adapter.etrade")
public record ETradeProperties(
    String baseUrl,
    String clientId,
    String clientSecret,
    String cookieValue,
    String requestTimeout,
    int tokenTtlSeconds,
    String tokenPath,
    Map<String, EndpointConfig> validations
) {
    public record EndpointConfig(
        String path,
        String method,
        Map<String, String> requestMapping
    ) {}
}
