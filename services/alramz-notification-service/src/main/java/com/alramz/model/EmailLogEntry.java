package com.alramz.model;

import java.time.LocalDateTime;

/** One email_log audit row. bodySha256 is the hex SHA-256 of the rendered body, never the body itself. */
public record EmailLogEntry(
        String templateId,
        String to,
        String cc,
        String bcc,
        String bodySha256,
        String language,
        LocalDateTime dateTime,
        String invoker,
        String ipAddress,
        String attachmentsMetaData
) {
}
