package com.alramz.exception;

import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

/**
 * Every DMS### errorCode this service returns in {@code GenericResponse}, with its HTTP status (brief 7.2,
 * no renumbering). Messages never contain metadata values, file names, Open Bee messages, URLs or keys.
 */
public enum ErrorCode {

    DMS101(HttpStatus.BAD_REQUEST, "Invalid request"),
    DMS102(HttpStatus.BAD_REQUEST, "Filing category is not available to this consumer"),
    DMS103(HttpStatus.BAD_REQUEST, "Metadata is not defined on this filing category"),
    DMS104(HttpStatus.BAD_REQUEST, "Mandatory metadata is missing"),
    DMS105(HttpStatus.BAD_REQUEST, "Invalid metadata value"),
    DMS106(HttpStatus.CONFLICT, "A document with this name already exists and overwrite is false"),
    DMS107(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the maximum size of %s"),
    DMS108(HttpStatus.NOT_FOUND, "Document not found"),
    DMS109(HttpStatus.FORBIDDEN, "Consumer is not allowed to perform this operation"),
    DMS110(HttpStatus.BAD_REQUEST, "Metadata type not supported by this service"),
    DMS201(HttpStatus.BAD_GATEWAY, "Unexpected response from the document management system"),
    DMS202(HttpStatus.BAD_GATEWAY, "Document management system rejected the service credentials"),
    DMS203(HttpStatus.SERVICE_UNAVAILABLE, "Document management system unavailable"),
    DMS299(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");

    private static final long MB = 1024L * 1024L;
    private static final long KB = 1024L;

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return name();
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** DMS107's text rendered with the configured limit, e.g. "File exceeds the maximum size of 10 MB". */
    public static String fileTooLargeMessage(DataSize maxFileSize) {
        return String.format(DMS107.defaultMessage, format(maxFileSize));
    }

    /** DMS101 text for a page size outside 1..max (service check and bean validation say the same thing). */
    public static String pageSizeMessage(int maxPageSize) {
        return "pageSize must be between 1 and " + maxPageSize;
    }

    static String format(DataSize size) {
        long bytes = size == null ? 0 : size.toBytes();
        if (bytes > 0 && bytes % MB == 0) {
            return bytes / MB + " MB";
        }
        if (bytes > 0 && bytes % KB == 0) {
            return bytes / KB + " KB";
        }
        return bytes + " bytes";
    }
}
