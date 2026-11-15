-- V31.1 (compensating, NOT part of normal rollout): puts back V14's single-column index on billing_payments.user_id
-- and drops V31's (#767) composite one. No data is touched: GET /api/v1/billing/payments/me keeps answering the
-- same, sorting each user's payments instead of reading them in index order.
--
-- Deliberately NOT under classpath:db/migration: the application and every
-- integration test that migrates that location to latest would apply it
-- right after V31 and revert the index. This file is an operator-invoked
-- escape hatch only, applied by explicitly adding BOTH classpath:db/migration
-- and this location to Flyway's locations with -target=31.1. The rollbacks
-- below 31 stay unapplied, so run with ignoreMigrationPatterns=*:ignored.
ALTER TABLE billing_payments
    DROP KEY idx_billing_payments_user_created,
    ADD KEY idx_billing_payments_user_id (user_id);
