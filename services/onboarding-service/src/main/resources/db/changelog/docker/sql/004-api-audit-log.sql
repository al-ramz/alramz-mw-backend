--liquibase formatted sql

--changeset alramz:api-audit-log-table

CREATE TABLE IF NOT EXISTS api_audit_log (
    id BIGSERIAL PRIMARY KEY,
    correlation_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    service_name VARCHAR(100) NOT NULL DEFAULT 'data-validation-service',
    controller_name VARCHAR(200),
    api_endpoint VARCHAR(500) NOT NULL,
    method VARCHAR(10) NOT NULL,
    request JSON,
    response JSON,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'FAILURE')),
    status_code INTEGER,
    exception_cause TEXT,
    exception_class VARCHAR(500),
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_audit_log_correlation_id ON api_audit_log(correlation_id);
CREATE INDEX IF NOT EXISTS idx_api_audit_log_created_at ON api_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_api_audit_log_direction_status ON api_audit_log(direction, status);
CREATE INDEX IF NOT EXISTS idx_api_audit_log_endpoint ON api_audit_log(api_endpoint);

--rollback DROP TABLE IF EXISTS api_audit_log;
