package com.alramz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CacheAuditService {

    private static final Logger log = LoggerFactory.getLogger(CacheAuditService.class);
    private final NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate;

    public CacheAuditService(NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate) {
        this.middlewareNamedParameterJdbcTemplate = middlewareNamedParameterJdbcTemplate;
    }

    @Async("cacheAuditExecutor")
    public void logAuditAsync(String mappingName, String cacheKey, String eventType, String triggeredBy, String triggerSource) {
        String sql = "INSERT INTO cache_audit_log (mapping_name, cache_key, event_type, triggered_by, trigger_source, details) " +
                "VALUES (:mappingName, :cacheKey, :eventType, :triggeredBy, :triggerSource, :details)";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("mappingName", mappingName);
        params.addValue("cacheKey", cacheKey);
        params.addValue("eventType", eventType);
        params.addValue("triggeredBy", triggeredBy);
        params.addValue("triggerSource", triggerSource);
        params.addValue("details", null);

        try {
            middlewareNamedParameterJdbcTemplate.update(sql, params);
        } catch (Exception e) {
            log.error("Failed to insert cache audit log: {}", e.getMessage(), e);
        }
    }
}
