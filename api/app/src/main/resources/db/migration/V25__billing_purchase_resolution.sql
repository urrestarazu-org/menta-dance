-- V25: admin resolution of an EXCEPTION purchase (#539). The status column stays VARCHAR(20) and
-- simply gains the terminal value RESOLVED; the audited decision lives in four additive, nullable
-- columns that are only set together with status = 'RESOLVED'. Existing rows need no backfill.
--
-- V24 is the current head of classpath:db/migration -- V25 is the next free version.

ALTER TABLE billing_purchases
    ADD COLUMN resolution VARCHAR(20) NULL,
    ADD COLUMN resolution_note VARCHAR(500) NULL,
    ADD COLUMN resolved_by BINARY(16) NULL,
    ADD COLUMN resolved_at DATETIME(3) NULL;
