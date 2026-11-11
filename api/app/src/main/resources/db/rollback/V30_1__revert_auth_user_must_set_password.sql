-- V30.1 (compensating, NOT part of normal rollout): drops the must_set_password column from auth_users
-- (V30, #378).
-- WARNING: accounts created by an administrator that are still pending lose the marker, so activating them no
-- longer asks the holder for a password and their unusable placeholder hash stays in place: delete or recreate
-- those accounts before rolling back. Accounts and their credentials are otherwise untouched.
--
-- Deliberately NOT under classpath:db/migration: the application and every
-- integration test that migrates that location to latest would apply it
-- right after V30 and revert the feature. This file is an operator-invoked
-- escape hatch only, applied by explicitly adding BOTH classpath:db/migration
-- and this location to Flyway's locations with -target=30.1. The rollbacks
-- below 30 stay unapplied, so run with ignoreMigrationPatterns=*:ignored.
ALTER TABLE auth_users
    DROP COLUMN must_set_password;
