--liquibase formatted sql
--changeset gaurav:002-notification-email-tables

CREATE TABLE IF NOT EXISTS email_configurations (
    id VARCHAR(20) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    number_of_placeholders INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    from_email VARCHAR(254) NOT NULL,
    has_attachments SMALLINT,
    ar_subject VARCHAR(200),
    en_subject VARCHAR(200),
    attachments_max_size VARCHAR(20),
    number_of_attachments INTEGER,
    type_of_attachments VARCHAR(50),
    is_bulk SMALLINT NOT NULL DEFAULT 0,
    ar_body TEXT,
    en_body TEXT,
    default_recipient VARCHAR(200),
    CONSTRAINT email_configurations_pk PRIMARY KEY (id),
    CONSTRAINT email_configurations_template_name_uk UNIQUE (template_name),
    CONSTRAINT email_configurations_is_bulk_ck CHECK (is_bulk IN (0, 1))
);

CREATE TABLE IF NOT EXISTS email_log (
    id BIGSERIAL PRIMARY KEY,
    template_id VARCHAR(20) NOT NULL,
    to_email VARCHAR(4000) NOT NULL,
    cc_email VARCHAR(4000),
    bcc_email VARCHAR(4000),
    email_body VARCHAR(64) NOT NULL,
    language VARCHAR(2) NOT NULL,
    date_time TIMESTAMP NOT NULL,
    invoker VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    attachments_meta_data TEXT
);

CREATE INDEX IF NOT EXISTS idx_email_log_template_invoker_time ON email_log(template_id, invoker, date_time);

--rollback DROP TABLE IF EXISTS email_log; DROP TABLE IF EXISTS email_configurations;
