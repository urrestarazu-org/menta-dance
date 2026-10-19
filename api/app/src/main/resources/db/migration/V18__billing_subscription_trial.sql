-- V18: admin-assigned trial subscription (US-BILLING-012, #131).
--
-- Three independent additions to billing_subscriptions, in the same migration
-- because they touch the same aggregate for one logical change (design A3):
--
--   * payment_id relaxed to NULL (design A1): a TRIAL subscription has no
--     Payment. The existing UNIQUE index (uq_billing_subscriptions_payment_id,
--     V14) is untouched -- MySQL UNIQUE indexes admit any number of NULLs.
--   * type + the TrialGrant audit columns (design A3, D5): six new columns,
--     `type` NOT NULL DEFAULT 'PAID' so every existing row back-fills without
--     a data pass, and the four grant columns nullable -- written together,
--     exactly once, by Subscription.trial(...), same discipline as V17's
--     cancellation columns.
--   * version (design A14): optimistic locking so the automatic expiry sweep
--     and a concurrent cancellation cannot silently overwrite each other.
--     NOT NULL DEFAULT 0 -- exactly what a freshly mapped entity carries, so
--     no existing row needs a real backfill value.
--
-- A17's rehydration-safety claim rests on this migration alone: every
-- pre-existing row has payment_id NOT NULL, so it back-fills as
-- type = 'PAID' with all four grant columns NULL -- a legal PAID pairing --
-- and the trial path is the first ever writer of a NULL payment_id, always
-- paired with type = 'TRIAL' and a non-null grant.
--
-- New index (design A2): the automatic expiry sweep reads by
-- (status, end_date) with no user_id, so the existing
-- idx_billing_subscriptions_user_status would force a full scan; this index
-- is the sweep's only read path.
--
-- Rollback: revert this migration plus a compensating migration dropping
-- these six columns and restoring payment_id NOT NULL -- only after
-- deleting or back-filling TRIAL rows, the only rows with a NULL payment_id.
ALTER TABLE billing_subscriptions
    MODIFY COLUMN payment_id BINARY(16) NULL;

ALTER TABLE billing_subscriptions
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'PAID' AFTER cancellation_reason,
    ADD COLUMN granted_at DATETIME(3) NULL AFTER type,
    ADD COLUMN granted_by BINARY(16) NULL AFTER granted_at,
    ADD COLUMN grant_reason VARCHAR(500) NULL AFTER granted_by,
    ADD COLUMN grant_days INT NULL AFTER grant_reason,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER grant_days;

ALTER TABLE billing_subscriptions
    ADD KEY idx_billing_subscriptions_status_end_date (status, end_date);
