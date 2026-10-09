package com.alramz.audit;

import java.util.UUID;

public record ApiAuditLog(
        UUID correlationId,
        String direction,
        String serviceName,
        String controllerName,
        String apiEndpoint,
        String method,
        Object request,
        Object response,
        String status,
        Integer statusCode,
        String exceptionCause,
        String exceptionClass,
        Long durationMs,
        java.time.Instant createdAt
) {}
