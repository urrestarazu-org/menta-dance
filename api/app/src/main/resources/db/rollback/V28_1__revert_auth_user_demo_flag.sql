-- V28.1 (compensating, NOT part of normal rollout): drops the demo flag column
-- from auth_users (V28, #698).
-- WARNING: this DESTROYS the demo/test marker; afterwards outgoing emails are no
-- longer suppressed for those accounts. Accounts and their credentials are untouched.
--
-- Deliberately NOT under classpath:db/migration: the application and every
-- integration test that migrates that location to latest would apply it
-- right after V28 and revert the feature. This file is an operator-invoked
-- escape hatch only, applied by explicitly adding BOTH classpath:db/migration
-- and this location to Flyway's locations with -target=28.1. V20.2, V21 and the
-- other rollbacks sit below 28 and stay unapplied, so run with
-- ignoreMigrationPatterns=*:ignored.
ALTER TABLE auth_users
    DROP COLUMN demo;
