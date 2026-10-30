package com.alramz.model;

/** One email_configurations row. Hand-written: not an API model. */
public record EmailTemplate(
        String id,
        String templateName,
        int numberOfPlaceholders,
        int maxAttempts,
        String fromEmail,
        String enSubject,
        String arSubject,
        String enBody,
        String arBody,
        String attachmentsMaxSize,
        Integer numberOfAttachments,
        String typeOfAttachments,
        boolean bulk
) {
}
