-- V6: virtual course catalog read model (US-VIRTUAL-001).
-- virtual_modules/virtual_lessons carry only what's needed to count and sum
-- duration for the catalog summary; full lesson content is out of scope
-- (US-VIRTUAL-002). virtual_lessons denormalizes course_id (in addition to
-- module_id) so counting/summing per course is a flat WHERE, never a JOIN --
-- mirrors billing_plan_courses' flat-query rationale (no JPA relationships,
-- no N+1).

CREATE TABLE virtual_courses (
    id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    short_description VARCHAR(500) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    category VARCHAR(100) NOT NULL,
    level VARCHAR(20) NOT NULL,
    is_premium BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_virtual_courses_status_id (status, id)
);

CREATE TABLE virtual_modules (
    id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_virtual_modules_course_id (course_id),
    CONSTRAINT fk_virtual_modules_course
        FOREIGN KEY (course_id) REFERENCES virtual_courses (id)
);

CREATE TABLE virtual_lessons (
    id BINARY(16) NOT NULL,
    module_id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    duration_minutes INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_virtual_lessons_module_id (module_id),
    KEY idx_virtual_lessons_course_id (course_id),
    CONSTRAINT fk_virtual_lessons_module
        FOREIGN KEY (module_id) REFERENCES virtual_modules (id),
    CONSTRAINT fk_virtual_lessons_course
        FOREIGN KEY (course_id) REFERENCES virtual_courses (id),
    CONSTRAINT chk_virtual_lessons_duration_non_negative CHECK (duration_minutes >= 0)
);
