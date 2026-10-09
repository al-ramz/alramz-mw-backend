--liquibase formatted sql
--changeset gaurav:003-rename-to-application-workflow-locks

ALTER TABLE IF EXISTS cache_reload_lock RENAME TO application_workflow_locks;

ALTER INDEX IF EXISTS idx_cache_reload_lock_mapping RENAME TO idx_application_workflow_locks_mapping;
ALTER INDEX IF EXISTS idx_cache_reload_lock_status RENAME TO idx_application_workflow_locks_status;
ALTER INDEX IF EXISTS idx_cache_reload_lock_expires_at RENAME TO idx_application_workflow_locks_expires_at;
