package com.alramz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SchedulerLockService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLockService.class);
    private static final String INSTANCE_ID = UUID.randomUUID().toString();
    private static final String LOCK_STATUS = "IN_PROGRESS";

    private final NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate;

    public SchedulerLockService(NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate) {
        this.middlewareNamedParameterJdbcTemplate = middlewareNamedParameterJdbcTemplate;
    }

    public boolean tryAcquireLock(String jobId, Duration leaseDuration) {
        String sql = "INSERT INTO application_workflow_locks (mapping_name, locked_by, status, expires_at) " +
                "VALUES (:jobId, :lockedBy, :status, :expiresAt) " +
                "ON CONFLICT (mapping_name) " +
                "DO UPDATE SET locked_at = EXCLUDED.locked_at, locked_by = EXCLUDED.locked_by, " +
                "status = EXCLUDED.status, completed_at = NULL, expires_at = EXCLUDED.expires_at " +
                "WHERE application_workflow_locks.expires_at < NOW() AND application_workflow_locks.status = :status";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("jobId", jobId);
        params.addValue("lockedBy", INSTANCE_ID);
        params.addValue("status", LOCK_STATUS);
        params.addValue("expiresAt", LocalDateTime.now().plus(leaseDuration));

        if (log.isDebugEnabled()) {
            log.debug("Executing try acquire lock SQL: {} | params: jobId={}, lockedBy={}, status={}, expiresAt={}",
                    sql, jobId, INSTANCE_ID, LOCK_STATUS, LocalDateTime.now().plus(leaseDuration));
        }

        try {
            int updated = middlewareNamedParameterJdbcTemplate.update(sql, params);
            return updated > 0;
        } catch (Exception e) {
            log.error("Failed to acquire scheduler lock for job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    public void releaseLock(String jobId) {
        String sql = "DELETE FROM application_workflow_locks WHERE mapping_name = :jobId AND locked_by = :lockedBy";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("jobId", jobId);
        params.addValue("lockedBy", INSTANCE_ID);

        if (log.isDebugEnabled()) {
            log.debug("Executing release lock SQL: {} | params: jobId={}, lockedBy={}", sql, jobId, INSTANCE_ID);
        }

        try {
            middlewareNamedParameterJdbcTemplate.update(sql, params);
        } catch (Exception e) {
            log.error("Failed to release scheduler lock for job {}: {}", jobId, e.getMessage(), e);
        }
    }

    public void cleanStaleLocks(String jobId) {
        String sql = "DELETE FROM application_workflow_locks WHERE mapping_name = :jobId AND expires_at < NOW()";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("jobId", jobId);

        if (log.isDebugEnabled()) {
            log.debug("Executing clean stale locks SQL: {} | params: jobId={}", sql, jobId);
        }

        try {
            middlewareNamedParameterJdbcTemplate.update(sql, params);
        } catch (Exception e) {
            log.warn("Failed to clean stale scheduler locks for job {}: {}", jobId, e.getMessage(), e);
        }
    }
}
