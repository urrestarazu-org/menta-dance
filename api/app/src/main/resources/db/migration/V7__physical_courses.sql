-- V7: physical course/session availability read model (US-PHYSICAL-003).
-- physical_capacity_assignments/physical_capacity_holds exist so
-- availableSpots can be computed as a live COUNT at read time -- never a
-- cached counter that could drift under concurrent writes (see
-- PhysicalSession's Javadoc). This issue only reads; no endpoint here
-- writes to either table -- that write path is a separate, future issue.

CREATE TABLE physical_courses (
    id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    professor_name VARCHAR(255) NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    level VARCHAR(20) NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_physical_courses_status_id (status, id)
);

CREATE TABLE physical_sessions (
    id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    scheduled_at DATETIME(3) NOT NULL,
    capacity INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_physical_sessions_course_scheduled (course_id, scheduled_at),
    CONSTRAINT fk_physical_sessions_course
        FOREIGN KEY (course_id) REFERENCES physical_courses (id)
);

-- A row here IS a confirmed assignment (no PENDING/CANCELLED state stored --
-- cancellation removes the row); unique per session+student so the same
-- student can never double-book the same session (docs/22-DATA-MODEL.md,
-- Physical section).
CREATE TABLE physical_capacity_assignments (
    id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    student_id BINARY(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_physical_assignment_session_student (session_id, student_id),
    KEY idx_physical_assignments_session (session_id),
    CONSTRAINT fk_physical_assignments_session
        FOREIGN KEY (session_id) REFERENCES physical_sessions (id)
);

-- Technical holds: a short-lived reservation of a spot while a purchase is
-- in flight (Billing's CapacityHold, docs/06-BILLING-API.md "Pagos
-- presenciales"). expires_at is the sole liveness signal -- a hold with no
-- explicit release is simply ignored once it expires, no cleanup job
-- required for this issue's read path.
CREATE TABLE physical_capacity_holds (
    id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_physical_holds_session_expires (session_id, expires_at),
    CONSTRAINT fk_physical_holds_session
        FOREIGN KEY (session_id) REFERENCES physical_sessions (id)
);
