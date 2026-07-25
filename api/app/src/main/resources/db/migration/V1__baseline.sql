-- Initial schema baseline. Future changes must be additive, immutable Flyway migrations.
CREATE TABLE auth_users (
    id BINARY(16) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_users_email UNIQUE (email)
);
