package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
    int maxSubjectLength,
    int maxBodyLength,
    int maxAttachments,
    long maxAttachmentSizeBytes
) {}
