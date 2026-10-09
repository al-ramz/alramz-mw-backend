--liquibase formatted sql
--changeset gaurav:001-create-schedule-job-table

CREATE TABLE IF NOT EXISTS schedule_job (
    id BIGSERIAL PRIMARY KEY,
    job_group_name VARCHAR(100) NOT NULL,
    schedule_id VARCHAR(100) NOT NULL,
    worker_bean_name VARCHAR(100) NOT NULL,
    job_bean_names VARCHAR(200),
    job_parameter VARCHAR(500),
    schedule_mode VARCHAR(20) NOT NULL,
    cron_expr VARCHAR(100),
    delay BIGINT,
    interval_seconds BIGINT,
    enable VARCHAR(1) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_schedule_job_group_schedule ON schedule_job(job_group_name, schedule_id);
