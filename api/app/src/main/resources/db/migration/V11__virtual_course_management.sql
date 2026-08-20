-- V11: management fields for virtual_courses/virtual_modules/virtual_lessons
-- plus an append-only audit trail (US-VIRTUAL-006, #54).
-- All three tables are empty in every real environment: V6 only ever
-- CREATE TABLE'd them, and no endpoint until this one can write a row. Safe
-- to add NOT NULL columns without a default.

ALTER TABLE virtual_courses
    ADD COLUMN description TEXT NOT NULL AFTER short_description,
    ADD COLUMN professor_id BINARY(16) NOT NULL AFTER description;

ALTER TABLE virtual_modules
    ADD COLUMN display_order INT NOT NULL AFTER title;

ALTER TABLE virtual_lessons
    ADD COLUMN description TEXT NOT NULL AFTER title,
    ADD COLUMN video_id VARCHAR(128) NOT NULL AFTER description,
    ADD COLUMN is_free BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN display_order INT NOT NULL;

CREATE TABLE virtual_course_audit (
    id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    action VARCHAR(50) NOT NULL,
    previous_value TEXT NULL,
    new_value TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_virtual_course_audit_course_id (course_id)
);
