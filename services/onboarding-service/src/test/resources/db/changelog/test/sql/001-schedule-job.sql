--liquibase formatted sql
--changeset gaurav:001-create-schedule-job-table

CREATE TABLE schedule_job (
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

--changeset gaurav:002-seed-schedule-job-data

INSERT INTO schedule_job (job_group_name, schedule_id, worker_bean_name, job_bean_names, job_parameter, schedule_mode, cron_expr, delay, interval_seconds, enable, created_at, updated_at)
VALUES ('dataValidation', 'dataValidationHealthCheck', 'dataValidationJobRunner', 'dataValidationJobRunner', '', 'CRON_EXP', '0 */5 * * * *', NULL, NULL, 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);