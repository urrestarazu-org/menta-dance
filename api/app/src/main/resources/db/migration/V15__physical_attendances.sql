-- V15: physical check-in attendances (US-PHYSICAL-001).
-- One row per confirmed check-in. UNIQUE(session_id, user_id) is the
-- schema-level idempotency guard the use case relies on: a second scan of
-- the same QR (escenario 5) reads this row back instead of racing a
-- duplicate INSERT (see AttendanceRepository's Javadoc). kind is a plain
-- VARCHAR mirroring physical_sessions.status/physical_courses.status,
-- consistent with the rest of this schema's enum encoding.

CREATE TABLE physical_attendances (
    id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_physical_attendances_session_user (session_id, user_id),
    KEY idx_physical_attendances_session (session_id),
    CONSTRAINT fk_physical_attendances_session
        FOREIGN KEY (session_id) REFERENCES physical_sessions (id)
);
