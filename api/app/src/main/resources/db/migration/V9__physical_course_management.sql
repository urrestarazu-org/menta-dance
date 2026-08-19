-- V9: management fields for physical_courses (US-PHYSICAL-005, #42).
-- physical_courses is empty in every real environment: V7 only ever
-- CREATE TABLE'd it, and no endpoint until this one can write a row. Safe
-- to add NOT NULL columns without a default.

ALTER TABLE physical_courses
    ADD COLUMN description TEXT NOT NULL AFTER title,
    ADD COLUMN professor_id BINARY(16) NOT NULL AFTER description,
    ADD COLUMN duration_minutes INT NOT NULL AFTER start_time;
