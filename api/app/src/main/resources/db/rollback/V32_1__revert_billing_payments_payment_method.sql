-- V32.1 (compensating, NOT part of normal rollout): drops the payment_method column from billing_payments
-- (V32, #771).
-- WARNING: the stored method is lost. Code from before #771 works the method out again at read time from the
-- external reference and the subscription's provider preference, which gives the same answer for every row the
-- checkouts wrote; a row loaded by hand that does not follow those conventions may read differently.
--
-- Deliberately NOT under classpath:db/migration: the application and every
-- integration test that migrates that location to latest would apply it
-- right after V32 and revert the feature. This file is an operator-invoked
-- escape hatch only, applied by explicitly adding BOTH classpath:db/migration
-- and this location to Flyway's locations with -target=32.1. The rollbacks
-- below 32 stay unapplied, so run with ignoreMigrationPatterns=*:ignored.
ALTER TABLE billing_payments
    DROP COLUMN payment_method;
