-- V24: append-only billing_audit_log (#33, US-BILLING-005, design D4/C7) and the reconciliation-task
-- resolution columns (D2/C11). billing_audit_log mirrors physical_device_audit's (V23) shape: one
-- row per admin decision (approve/reject/correction), never updated or deleted. Correction evidence
-- is composed into the reason column as "{motivo} | evidencia: {evidence}" (D4/C7) rather than a
-- dedicated column, per design's Open Questions.
--
-- V23 is the current head of classpath:db/migration -- V24 is the next free, disjoint version.
--
-- resolved/resolved_at/resolved_by are additive and nullable/defaulted: WebhookVerificationWorker's
-- existing five-argument insert into billing_reconciliation_tasks stays valid unmodified, and every
-- pre-existing row reads as unresolved with no backfill required (design C11).

CREATE TABLE billing_audit_log (
    id BINARY(16) NOT NULL,
    payment_id BINARY(16) NOT NULL,
    admin_id BINARY(16) NOT NULL,
    action VARCHAR(20) NOT NULL,
    reason TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_billing_audit_log_payment_id (payment_id),
    CONSTRAINT fk_billing_audit_log_payment
        FOREIGN KEY (payment_id) REFERENCES billing_payments (id)
);

ALTER TABLE billing_reconciliation_tasks
    ADD COLUMN resolved BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN resolved_at DATETIME(3) NULL,
    ADD COLUMN resolved_by BINARY(16) NULL;
