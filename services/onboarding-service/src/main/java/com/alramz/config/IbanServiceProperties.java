package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adapter.external-iban-service")
public record IbanServiceProperties (
    String baseUrl,
    String apiKey,
    String requestTimeout)
{}
