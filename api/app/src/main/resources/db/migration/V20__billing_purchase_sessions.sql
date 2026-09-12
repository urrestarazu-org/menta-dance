-- V20: Purchase becomes 1:N (#41, US-PHYSICAL-004, design A1). A MONTHLY
-- purchase spans several physical sessions; billing_purchases.physical_session_id
-- (V8) can only ever represent one. No code path in the repository has ever
-- created a multi-session purchase -- that capability does not exist before
-- this change -- so every pre-existing row is single-session by construction
-- and the backfill below is a total, lossless projection, not best-effort.
--
-- Ordered by an explicit `position` (design A1/A2): for every pre-existing
-- row that order is trivially [0] = its one session. `position` is what makes
-- the claim order durable and auditable rather than reconstructed, and is
-- what any future multi-session purchase (this change's own checkout path)
-- relies on.
--
-- No per-session status column: all-or-nothing assignment (design A3) makes
-- the parent Purchase's single FulfillmentStatus complete information.
CREATE TABLE billing_purchase_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    purchase_id BINARY(16) NOT NULL,
    position INT NOT NULL,
    physical_session_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_purchase_sessions_purchase_position (purchase_id, position),
    CONSTRAINT fk_billing_purchase_sessions_purchase
        FOREIGN KEY (purchase_id) REFERENCES billing_purchases (id)
);

INSERT INTO billing_purchase_sessions (purchase_id, position, physical_session_id)
SELECT id, 0, physical_session_id FROM billing_purchases;

ALTER TABLE billing_purchases
    DROP COLUMN physical_session_id;
