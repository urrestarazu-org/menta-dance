-- V27.1 (compensating, NOT part of normal rollout): drops the profile columns
-- (first_name, last_name, phone, birth_date) from auth_users (V27, #652).
-- WARNING: this DESTROYS the personal data collected at registration; export
-- it first if it must be kept. Accounts and their credentials are untouched.
--
-- Deliberately NOT under classpath:db/migration: the application and every
-- integration test that migrates that location to latest would apply it
-- right after V27 and revert the feature. This file is an operator-invoked
-- escape hatch only, applied by explicitly adding BOTH classpath:db/migration
-- and this location to Flyway's locations with -target=27.1. V20.2 and V21 sit
-- below 27 and stay unapplied, so run with ignoreMigrationPatterns=*:ignored.
ALTER TABLE auth_users
    DROP COLUMN first_name,
    DROP COLUMN last_name,
    DROP COLUMN phone,
    DROP COLUMN birth_date;
