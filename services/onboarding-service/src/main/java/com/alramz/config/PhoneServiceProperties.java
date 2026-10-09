package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adapter.external-veriphone-service")
public record PhoneServiceProperties (
    String baseUrl,
    String apiKey,
    String requestTimeout)
{}
