-- V8: payments, fulfillment and the Mercado Pago webhook inbox (US-BILLING-002).
-- docs/06-BILLING-API.md ("Estados e idempotencia") and ADR-0038 are the
-- design authority. billing_webhook_inbox.dedupe_key is the actual dedup
-- mechanism: a unique constraint, never an exists-then-insert race.

CREATE TABLE billing_payments (
    id BINARY(16) NOT NULL,
    provider_payment_id VARCHAR(64) NOT NULL,
    expected_amount DECIMAL(10,2) NOT NULL,
    expected_currency CHAR(3) NOT NULL,
    expected_external_reference VARCHAR(128) NOT NULL,
    expected_merchant_account_id VARCHAR(64) NOT NULL,
    target_modality VARCHAR(20) NOT NULL,
    target_reference VARCHAR(64) NOT NULL,
    status_type VARCHAR(30) NOT NULL,
    status_reason VARCHAR(500) NULL,
    status_changed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_payments_provider_payment_id (provider_payment_id),
    KEY idx_billing_payments_status_type (status_type),
    CONSTRAINT chk_billing_payments_amount_non_negative CHECK (expected_amount >= 0)
);

CREATE TABLE billing_purchases (
    id BINARY(16) NOT NULL,
    payment_id BINARY(16) NOT NULL,
    physical_session_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_purchases_payment_id (payment_id),
    CONSTRAINT fk_billing_purchases_payment
        FOREIGN KEY (payment_id) REFERENCES billing_payments (id)
);

CREATE TABLE billing_subscriptions (
    id BINARY(16) NOT NULL,
    payment_id BINARY(16) NOT NULL,
    virtual_course_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_subscriptions_payment_id (payment_id),
    CONSTRAINT fk_billing_subscriptions_payment
        FOREIGN KEY (payment_id) REFERENCES billing_payments (id)
);

CREATE TABLE billing_webhook_inbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dedupe_key VARCHAR(160) NOT NULL,
    provider_payment_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NULL,
    last_error VARCHAR(1000) NULL,
    received_at DATETIME(3) NOT NULL,
    processed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_webhook_inbox_dedupe_key (dedupe_key),
    KEY idx_billing_webhook_inbox_status_next_attempt (status, next_attempt_at)
);

CREATE TABLE billing_reconciliation_tasks (
    id BINARY(16) NOT NULL,
    payment_id BINARY(16) NULL,
    provider_payment_id VARCHAR(64) NOT NULL,
    reason TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_billing_reconciliation_tasks_payment_id (payment_id),
    CONSTRAINT fk_billing_reconciliation_tasks_payment
        FOREIGN KEY (payment_id) REFERENCES billing_payments (id)
);
