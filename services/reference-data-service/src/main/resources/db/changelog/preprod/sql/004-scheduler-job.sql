--liquibase formatted sql
--changeset gaurav:004-scheduler-job

INSERT INTO schedule_job (job_group_name, schedule_id, worker_bean_name, job_bean_names,
    job_parameter, schedule_mode, cron_expr, delay, interval_seconds, enable, created_at, updated_at)
VALUES ('referenceData', 'cacheReloadJob', 'cacheReloadJobRunner',
    '', 'global-config', 'CRON_EXP', '0 */10 * * * *', NULL, NULL, 'Y',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (job_group_name, schedule_id) DO NOTHING;
