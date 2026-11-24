-- V33.1 (compensating, NOT part of normal rollout): drops the video_id index added by V33 (#841). No data is
-- touched: the usage lookup keeps answering the same, scanning virtual_lessons instead of using the index.
--
-- Deliberately NOT under classpath:db/migration: the application and every
-- integration test that migrates that location to latest would apply it
-- right after V33 and revert the index. This file is an operator-invoked
-- escape hatch only, applied by explicitly adding BOTH classpath:db/migration
-- and this location to Flyway's locations with -target=33.1. The rollbacks
-- below 33 stay unapplied, so run with ignoreMigrationPatterns=*:ignored.
DROP INDEX idx_virtual_lessons_video_id ON virtual_lessons;
