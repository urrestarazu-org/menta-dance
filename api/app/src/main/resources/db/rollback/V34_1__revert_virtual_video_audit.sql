-- V34.1 (compensating, NOT part of normal rollout): drops the video audit table (V34, #841).
-- WARNING: this DESTROYS the audit trail of video renames and deletions. Lessons and the library are untouched.
--
-- Deliberately NOT under classpath:db/migration: the application and every
-- integration test that migrates that location to latest would apply it
-- right after V34 and revert the feature. This file is an operator-invoked
-- escape hatch only, applied by explicitly adding BOTH classpath:db/migration
-- and this location to Flyway's locations with -target=34.1. The rollbacks
-- below 34 stay unapplied, so run with ignoreMigrationPatterns=*:ignored.
DROP TABLE virtual_video_audit;
