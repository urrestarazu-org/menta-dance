-- V20.1: correlates a physical capacity hold with the payment that owns it
-- (#208, US-PHYSICAL-004b, design B1). physical_capacity_holds is already
-- one row per session, so unlike billing_purchases -> billing_purchase_sessions
-- (V20) this needs no child table -- the correlation is two columns.
--
-- Numbered V20.1, not the V21 design.md sketches: classpath:db/rollback
-- already owns version 21 (V21__revert_billing_purchase_sessions.sql, #41),
-- and PurchaseSessionsMigrationIntegrationTest drives that revert by
-- migrating classpath:db/migration to latest and then targeting version 21
-- with classpath:db/rollback added. Any whole-number version >= 21 here
-- would make that "migrate to latest" already pass 21, turning its later
-- target(21) into an out-of-order validation failure -- a real regression to
-- an existing, passing test, not merely a version clash. A decimal version
-- between V20 and V21 keeps this migration entirely out of that test's way
-- while still applying immediately after V20 on every other path.
--
-- payment_id is NOT NULL: the table has zero production rows (no write path
-- exists yet -- see PhysicalCapacityHoldJpaEntity's prior javadoc), so NOT
-- NULL costs no backfill and closes off, by construction, a hold that could
-- never be converted or released.
--
-- converted_at is the conversion marker (design B3): a hold row is marked,
-- never deleted, on conversion, so redelivery of the same webhook can answer
-- "already converted" without recomputation. NULL means still active.
--
-- uq_physical_holds_payment_session makes hold creation idempotent per claim
-- and surfaces a double-claim at flush(), exactly like
-- uq_physical_assignment_session_student (V7). idx_physical_holds_payment
-- serves conversion and release, which both address holds by payment.
ALTER TABLE physical_capacity_holds
    ADD COLUMN payment_id BINARY(16) NOT NULL,
    ADD COLUMN converted_at DATETIME(3) NULL,
    ADD UNIQUE KEY uq_physical_holds_payment_session (payment_id, session_id),
    ADD KEY idx_physical_holds_payment (payment_id);
