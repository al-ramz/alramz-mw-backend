--liquibase formatted sql
--changeset gaurav:008-banks-countries-scedule

INSERT INTO schedule_job (job_group_name, schedule_id, worker_bean_name, job_bean_names,
    job_parameter, schedule_mode, cron_expr, delay, interval_seconds, enable, created_at, updated_at)
VALUES ('referenceData', 'cacheReloadForBanksJob', 'cacheReloadJobRunner',
    '', 'banks', 'CRON_EXP', '0 */1 * * * *', NULL, NULL, 'Y',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO schedule_job (job_group_name, schedule_id, worker_bean_name, job_bean_names,
    job_parameter, schedule_mode, cron_expr, delay, interval_seconds, enable, created_at, updated_at)
VALUES ('referenceData', 'cacheReloadForCountriesJob', 'cacheReloadJobRunner',
    '', 'countries', 'CRON_EXP', '0 */1 * * * *', NULL, NULL, 'Y',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
