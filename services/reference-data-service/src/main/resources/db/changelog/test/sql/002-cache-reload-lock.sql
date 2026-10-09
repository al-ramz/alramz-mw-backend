--liquibase formatted sql
--changeset gaurav:002-cache-reload-lock

CREATE TABLE IF NOT EXISTS cache_reload_lock (
    id BIGSERIAL PRIMARY KEY,
    mapping_name VARCHAR(100) NOT NULL UNIQUE,
    locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_by VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    completed_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cache_reload_lock_mapping ON cache_reload_lock(mapping_name);
CREATE INDEX IF NOT EXISTS idx_cache_reload_lock_status ON cache_reload_lock(status);
CREATE INDEX IF NOT EXISTS idx_cache_reload_lock_expires_at ON cache_reload_lock(expires_at);
