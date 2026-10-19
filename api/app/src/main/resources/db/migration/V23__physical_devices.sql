-- V23: physical device registry (#44, US-PHYSICAL-007, design C8). physical_devices holds one
-- row per registered QR check-in reader; only the SHA-256 hash of its secret is stored (D1), the
-- raw value never persists (D4). physical_device_audit is an append-only trail of every
-- register/rotate/revoke action, mirroring virtual_course_audit's column set (V11).
--
-- V22 is the current head of classpath:db/migration and classpath:db/rollback's highest is V21
-- (V21__revert_billing_purchase_sessions.sql, #41) -- V23 is free and disjoint in both namespaces
-- (design C8), same verification PhysicalAttendanceHistoryMigrationIntegrationTest already ran
-- for V22 (#39).
--
-- CHAR(64), not VARCHAR: a lowercase SHA-256 hex digest is fixed-width by construction.
-- uq_physical_devices_secret_hash is a real invariant -- two devices sharing a secret would make
-- a future hash lookup ambiguous, and it makes "rotation actually replaced the hash" enforceable
-- at the schema level. The audit FK is kept (V15's convention) even though virtual_course_audit
-- omits one: a device row is never deleted -- revocation is a status change -- so the FK can
-- never block the append-only trail, and it prevents orphan audit rows.

CREATE TABLE physical_devices (
    id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    location VARCHAR(160) NOT NULL,
    secret_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_physical_devices_secret_hash (secret_hash),
    KEY idx_physical_devices_status (status)
);

CREATE TABLE physical_device_audit (
    id BINARY(16) NOT NULL,
    device_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    action VARCHAR(50) NOT NULL,
    previous_value TEXT NULL,
    new_value TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_physical_device_audit_device (device_id),
    CONSTRAINT fk_physical_device_audit_device
        FOREIGN KEY (device_id) REFERENCES physical_devices (id)
);
