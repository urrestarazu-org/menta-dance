-- V2: forward-only schema extension for auth tokens and durable-local outbox.
-- Reference: ADRs 0025 (tokens), 0026 (outbox durable-local), 0027 (Flyway forward-only).
-- Authoritative: auth_users.token_version, auth_refresh_tokens, common_outbox_events.

-- Step 1: extend auth_users with token_version (ADR-0025).
-- Reset existing rows to 1 so pre-V2 sessions are still valid until their next login.
ALTER TABLE auth_users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 1;

-- Step 2: refresh tokens table (ADR-0025 strict per-family rotation).
-- PK BINARY(16) = UUID v4. token_hash stores SHA-256 hex digest of the opaque UUID refresh.
-- UNIQUE on token_hash prevents duplicate inserts; UNIQUE is the idempotency lock.
-- Index on (family_id, status) accelerates family-scoped revocation queries.
CREATE TABLE auth_refresh_tokens (
    id BINARY(16) NOT NULL,
    family_id BINARY(16) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    user_id BINARY(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    token_version BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    rotated_at DATETIME NULL,
    revoked_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_refresh_tokens_token_hash (token_hash),
    KEY idx_auth_refresh_tokens_family_status (family_id, status),
    KEY idx_auth_refresh_tokens_user (user_id)
);

-- Step 3: common_outbox_events table (ADR-0026 durable-local + ADR-0027 forward-only).
-- Doble UNIQUE: event_id (ULID portability) and (aggregate_id, event_type) (idempotency lock).
-- payload is JSON for portability; status drives the reconciler flow.
-- Index on (status, created_at) accelerates the PENDING pull-batch.
CREATE TABLE common_outbox_events (
    id BIGINT AUTO_INCREMENT NOT NULL,
    event_id CHAR(26) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    next_retry_at DATETIME NULL,
    created_at DATETIME(3) NOT NULL,
    processed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_common_outbox_event_id (event_id),
    UNIQUE KEY uk_common_outbox_aggregate_event_type (aggregate_id, event_type),
    KEY idx_common_outbox_status_created (status, created_at)
);
