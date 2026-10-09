--liquibase formatted sql
--changeset gaurav:003-cache-audit-log

CREATE TABLE IF NOT EXISTS cache_audit_log (
    id BIGSERIAL PRIMARY KEY,
    mapping_name VARCHAR(100) NOT NULL,
    cache_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('FLUSH', 'RELOAD', 'EVICTED')),
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    triggered_by VARCHAR(255) NOT NULL,
    trigger_source VARCHAR(100) NOT NULL,
    details TEXT
);

CREATE INDEX IF NOT EXISTS idx_cache_audit_log_mapping ON cache_audit_log(mapping_name);
CREATE INDEX IF NOT EXISTS idx_cache_audit_log_event_type ON cache_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_cache_audit_log_timestamp ON cache_audit_log(event_timestamp);
