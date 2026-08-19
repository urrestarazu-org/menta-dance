-- V10: management fields for physical_sessions (US-PHYSICAL-006, #43).
-- physical_sessions is empty in every real environment: no endpoint until
-- this one can write a row (#40 only reads, #42 does not touch sessions).
-- Safe to add a NOT NULL column without a default.

ALTER TABLE physical_sessions
    ADD COLUMN status VARCHAR(20) NOT NULL AFTER capacity,
    ADD COLUMN notes VARCHAR(500) NULL AFTER status;
