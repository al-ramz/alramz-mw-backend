--liquibase formatted sql
--changeset gaurav:004-notification-otp-tables

CREATE TABLE IF NOT EXISTS otp_configuration (
    message_id INTEGER NOT NULL,
    service_name VARCHAR(200) NOT NULL,
    expiry_in_seconds INTEGER NOT NULL,
    token_length INTEGER NOT NULL,
    sms_message_arabic VARCHAR(4000),
    sms_message_english VARCHAR(4000),
    max_attempts INTEGER NOT NULL,
    sent_via VARCHAR(20) NOT NULL,
    email_message_arabic TEXT,
    email_message_english TEXT,
    max_validation_attempts INTEGER,
    CONSTRAINT otp_configuration_pk PRIMARY KEY (message_id),
    CONSTRAINT otp_configuration_service_name_uk UNIQUE (service_name),
    CONSTRAINT otp_configuration_expiry_ck CHECK (expiry_in_seconds BETWEEN 1 AND 86400),
    CONSTRAINT otp_configuration_token_length_ck CHECK (token_length BETWEEN 4 AND 10),
    CONSTRAINT otp_configuration_max_attempts_ck CHECK (max_attempts >= 1),
    CONSTRAINT otp_configuration_sent_via_ck CHECK (sent_via IN ('SMS', 'EMAIL', 'EMAIL|SMS')),
    CONSTRAINT otp_configuration_max_validation_ck CHECK (max_validation_attempts IS NULL OR max_validation_attempts >= 1)
);

CREATE TABLE IF NOT EXISTS otp_management (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(200) NOT NULL,
    mobile_email VARCHAR(254) NOT NULL,
    otp VARCHAR(64) NOT NULL,
    creation_date_time TIMESTAMP NOT NULL,
    expiry_date_time TIMESTAMP NOT NULL,
    active VARCHAR(1) NOT NULL,
    status VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    validation_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT otp_management_active_ck CHECK (active IN ('0', '1')),
    CONSTRAINT otp_management_status_ck CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'VALIDATED'))
);

CREATE INDEX IF NOT EXISTS idx_otp_management_lookup ON otp_management(service_name, mobile_email, active);

CREATE TABLE IF NOT EXISTS otp_attempts (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(200) NOT NULL,
    mobile_email VARCHAR(254) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_date TIMESTAMP NOT NULL,
    CONSTRAINT otp_attempts_service_recipient_uk UNIQUE (service_name, mobile_email)
);

--rollback DROP TABLE IF EXISTS otp_attempts; DROP TABLE IF EXISTS otp_management; DROP TABLE IF EXISTS otp_configuration;
