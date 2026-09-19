-- Payment proof persistence for bank-transfer subscriptions (#31, US-BILLING-003, design C5).
--
-- Numbered V20.1.5, not the V21 design.md sketch: classpath:db/rollback already owns version 21
-- (V21__revert_billing_purchase_sessions.sql, #41), and PhysicalCapacityHoldMigrationIntegrationTest's
-- own Javadoc documents the exact trap a whole-number V21/V22 here falls into — it either collides
-- with that existing version 21 the moment both locations are scanned together, or (a whole-number
-- V22) pushes "migrate to latest" past both existing rollback checkpoints (20.2 and 21), turning
-- PurchaseSessionsMigrationIntegrationTest's and PhysicalCapacityHoldMigrationIntegrationTest's own
-- target(20.2)/target(21) reverts into out-of-order validation failures — confirmed by running the
-- full :api:app:test suite: both failed with exactly that FlywayException before this fix. A decimal
-- version strictly between the existing V20.1 and V20.2 (20 < 20.1 < 20.1.5 < 20.2 < 21) avoids every
-- one of those failure modes without touching either unrelated pre-existing test.
--
-- The unique key on payment_id means a replacement proof overwrites the single existing row for
-- that payment — there is no cleanup step to remember, it is a structural property of the schema.
CREATE TABLE billing_payment_proofs (
    id                BINARY(16)      NOT NULL,
    payment_id        BINARY(16)      NOT NULL,
    storage_key       VARCHAR(160)    NOT NULL,
    original_filename VARCHAR(255)    NOT NULL,
    content_type      VARCHAR(64)     NOT NULL,
    size_bytes        BIGINT UNSIGNED NOT NULL,
    uploaded_at       DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_payment_proofs_payment_id (payment_id),
    CONSTRAINT fk_billing_payment_proofs_payment
        FOREIGN KEY (payment_id) REFERENCES billing_payments (id)
);

-- Sweep support for the P5 72h expiry worker (design "Migration / Rollout"): the existing
-- idx_billing_payments_status_type index does not cover created_at, so the sweep's query
-- (status_type = 'AWAITING_MANUAL_VERIFICATION' AND created_at < :cutoff) would be a range scan
-- over every terminal payment without this composite index.
ALTER TABLE billing_payments
    ADD KEY idx_billing_payments_status_created (status_type, created_at);
