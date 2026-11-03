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
    // Compact-constructor parameters are written to the record fields implicitly; PMD misses that.
    @SuppressWarnings("PMD.UnusedAssignment")
    public EmailProperties {
        allowedSenders = allowedSenders == null ? List.of() : List.copyOf(allowedSenders);
    }
}
