package com.alramz.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * Mutable per-request facts for the single DMS summary log line, stored as a request attribute by
 * {@code DmsRequestSummaryInterceptor} and filled on the servlet thread by the service, the Open Bee client and
 * the exception handler. Holds IDs, codes and the file extension only: never names, values or keys.
 */
@Getter
@Setter
public class DmsRequestSummary {

    public static final String ATTRIBUTE = DmsRequestSummary.class.getName();

    private final long startNanos;
    private String operation;
    private String consumerId;
    private Long filingCategoryId;
    private Long documentId;
    private Integer openBeeStatus;
    private String dmsCode;
    private String extension;

    public DmsRequestSummary(String operation, long startNanos) {
        this.operation = operation;
        this.startNanos = startNanos;
    }

    /** The summary of the request bound to the current thread, if any (empty outside a DMS request). */
    public static Optional<DmsRequestSummary> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        Object value = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof DmsRequestSummary summary ? Optional.of(summary) : Optional.empty();
    }
}
