-- V19: per-student lesson progress (#52, US-VIRTUAL-005).
-- One mutable upsert row per (user_id, lesson_id): position_seconds tracks
-- the last saved playback position, completed/completed_at track lesson
-- completion independently (POST /complete never moves position, per
-- design.md decisions 3 and 7).
--
-- Surrogate id + unique (user_id, lesson_id) mirrors every other virtual
-- table (V6/V11) instead of a composite PK, while the unique key still gives
-- schema-level idempotency for the upsert. course_id is denormalized (V6
-- rationale) so the course aggregate stays a flat WHERE, never a JOIN.
--
-- No FK on user_id: virtual holds no FK into auth anywhere, the token
-- subject is authoritative. Both FKs are RESTRICT (no ON DELETE CASCADE),
-- matching V6/V11 -- lesson deletion only happens via
-- DeleteVirtualCourseUseCaseImpl, which requires DRAFT, a status students
-- can never have progress against, so RESTRICT is unreachable today; if it
-- ever fires it fails loud instead of silently destroying student history.
--
-- position_updated_at is nullable and is the resume-ordering key: a row
-- first created by POST /complete on a never-played lesson leaves it NULL,
-- the honest encoding of "never played" -- not a magic epoch sentinel.
-- updated_at is the generic row-audit timestamp, bumped by any write, never
-- used for resume ordering.
--
-- This is a pure additive CREATE TABLE: no backfill, no existing table
-- altered, so no dedicated Testcontainers migration-history test is needed
-- (unlike V18, which altered a table with pre-existing rows).
CREATE TABLE virtual_lesson_progress (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    lesson_id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    position_seconds INT NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    position_updated_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_virtual_lesson_progress_user_lesson (user_id, lesson_id),
    KEY idx_virtual_lesson_progress_user_course (user_id, course_id),
    CONSTRAINT fk_virtual_lesson_progress_lesson
        FOREIGN KEY (lesson_id) REFERENCES virtual_lessons (id),
    CONSTRAINT fk_virtual_lesson_progress_course
        FOREIGN KEY (course_id) REFERENCES virtual_courses (id),
    CONSTRAINT chk_virtual_lesson_progress_position CHECK (position_seconds >= 0)
);
