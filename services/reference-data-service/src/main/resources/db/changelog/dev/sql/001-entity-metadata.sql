--liquibase formatted sql
--changeset gaurav:001-entity-metadata.sql

CREATE TABLE entity_metadata (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(63) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    icon VARCHAR(100),
    searchable_fields JSONB,                 -- e.g. ["code","name"]
    soft_delete BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    is_dynamic BOOLEAN NOT NULL DEFAULT TRUE, -- always true under CSV-only creation; kept for future-proofing
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);