--liquibase formatted sql
--changeset gaurav:003-create-jwt-user-table

CREATE TABLE jwt_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    roles VARCHAR(500) NOT NULL,
    enable VARCHAR(1) NOT NULL,
    application VARCHAR(100),
    environment VARCHAR(50),
    created_at TIMESTAMP NOT NULL
);

--changeset gaurav:004-create-jwt-refresh-token-table

CREATE TABLE jwt_refresh_token (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    user_id VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    revoked VARCHAR(1) NOT NULL
);

--changeset gaurav:005-create-jwt-indexes

CREATE INDEX idx_jwt_user_username ON jwt_user(username);
CREATE INDEX idx_jwt_user_email ON jwt_user(email);
CREATE INDEX idx_refresh_token_token ON jwt_refresh_token(token);
CREATE INDEX idx_refresh_token_user_id ON jwt_refresh_token(user_id);
