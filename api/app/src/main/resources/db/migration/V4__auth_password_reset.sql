-- V4: password reset credentials and durable delivery envelope.
-- Mirrors V3's activation-token shape: raw reset tokens are never stored, the
-- digest supports lookup, and the short-lived AES-GCM envelope supports
-- crash-safe email delivery. Deliberately a separate table from
-- auth_activation_tokens: the two are independent security domains, and
-- sharing a table would couple their retention, indexes and blast radius.

CREATE TABLE auth_password_reset_tokens (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    delivery_ciphertext VARBINARY(1024) NULL,
    delivery_nonce BINARY(12) NULL,
    delivery_key_version SMALLINT UNSIGNED NULL,
    expires_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    invalidated_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_password_reset_token_hash (token_hash),
    KEY idx_auth_password_reset_user_active (user_id, used_at, invalidated_at),
    CONSTRAINT chk_auth_password_reset_terminal_state
        CHECK (used_at IS NULL OR invalidated_at IS NULL),
    CONSTRAINT chk_auth_password_reset_delivery_envelope
        CHECK (
            (delivery_ciphertext IS NULL
                AND delivery_nonce IS NULL
                AND delivery_key_version IS NULL)
            OR
            (delivery_ciphertext IS NOT NULL
                AND delivery_nonce IS NOT NULL
                AND delivery_key_version IS NOT NULL)
        )
);
