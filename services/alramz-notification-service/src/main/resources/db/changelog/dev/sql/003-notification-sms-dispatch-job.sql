--liquibase formatted sql
--changeset gaurav:003-notification-sms-dispatch-job

INSERT INTO schedule_job (job_group_name, schedule_id, worker_bean_name, job_bean_names,
    job_parameter, schedule_mode, cron_expr, delay, interval_seconds, enable, created_at, updated_at)
VALUES ('notification', 'smsDispatch', 'smsDispatchJobRunner', 'smsDispatchJobRunner',
    '', 'FIXED_DELAY', NULL, 15000, NULL, 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

--rollback DELETE FROM schedule_job WHERE job_group_name = 'notification' AND schedule_id = 'smsDispatch';
