-- V29.1 (compensating, NOT part of normal rollout): drops the user audit table (V29, #378).
-- WARNING: this DESTROYS the audit trail of role changes. Accounts and their roles are untouched.
--
-- Deliberately NOT under classpath:db/migration: the application and every
-- integration test that migrates that location to latest would apply it
-- right after V29 and revert the feature. This file is an operator-invoked
-- escape hatch only, applied by explicitly adding BOTH classpath:db/migration
-- and this location to Flyway's locations with -target=29.1. The rollbacks
-- below 29 stay unapplied, so run with ignoreMigrationPatterns=*:ignored.
DROP TABLE auth_user_audit;
