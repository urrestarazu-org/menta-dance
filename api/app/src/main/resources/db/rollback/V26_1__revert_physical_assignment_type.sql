-- V26.1 (compensating, NOT part of normal rollout): drops assignment_type
-- from physical_capacity_assignments (V26, #521). The column is derived
-- metadata (MONTHLY or INDIVIDUAL) so dropping it loses no other data.
--
-- Deliberately NOT under classpath:db/migration: the application and every
-- integration test that migrates that location to latest would apply it
-- right after V26 and revert the feature. This file is an operator-invoked
-- escape hatch only, applied by explicitly adding BOTH classpath:db/migration
-- and this location to Flyway's locations with -target=26.1. V20.2 and V21 sit
-- below 26 and stay unapplied, so run with ignoreMigrationPatterns=*:ignored.
ALTER TABLE physical_capacity_assignments
    DROP COLUMN assignment_type;
