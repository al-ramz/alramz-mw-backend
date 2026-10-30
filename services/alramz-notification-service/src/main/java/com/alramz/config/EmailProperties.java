package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
    int maxSubjectLength,
    int maxBodyLength,
    int maxAttachments,
    long maxTotalAttachmentSizeBytes,
    List<String> allowedSenders
) {
    public EmailProperties {
        allowedSenders = allowedSenders == null ? List.of() : List.copyOf(allowedSenders);
    }
}
