-- V17: subscription cancellation audit trail (US-BILLING-011, #130).
--
-- Adds who cancelled a subscription, when, and why as three nullable columns rather than a
-- separate audit table: the three values are written together, exactly once, by
-- Subscription.cancel(...), and are never queried independently (see the domain Cancellation
-- value object).
--
-- No backfill and no new index (A8): pre-existing CANCELLED rows come from the
-- payment-never-settled path (escenario 6 of US-BILLING-010, Subscription.cancelled()) and
-- truthfully have no actor or reason -- they map back to Optional.empty() in the domain via
-- SubscriptionJpaMapper. The existing idx_billing_subscriptions_user_status index already
-- covers the lookups this feature needs; a 4th index would tax every checkout insert for no
-- read this feature performs.
--
-- Rollback = revert this migration plus a compensating DROP COLUMN; access retention derives
-- from end_date only, so it stays correct either way.
ALTER TABLE billing_subscriptions
    ADD COLUMN cancelled_at DATETIME(3) NULL AFTER created_at,
    ADD COLUMN cancelled_by BINARY(16) NULL AFTER cancelled_at,
    ADD COLUMN cancellation_reason VARCHAR(500) NULL AFTER cancelled_by;
