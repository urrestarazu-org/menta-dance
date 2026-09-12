-- V21 (compensating, NOT part of normal rollout -- design.md "Migration /
-- Rollout"): re-adds billing_purchases.physical_session_id and drops
-- billing_purchase_sessions (V20). This is a LOSSY operation for any
-- purchase covering more than one session, so it must REFUSE rather than
-- silently truncate to the first session.
--
-- Deliberately NOT under classpath:db/migration (spring.flyway.locations,
-- api/app/src/main/resources/application.yml): the application, and every
-- integration test that drives Flyway against that same location, would
-- apply this automatically on the very next "migrate to latest" right after
-- V20 -- reverting the feature this whole change ships before it ever runs.
-- This file is an operator-invoked escape hatch only, applied by explicitly
-- adding BOTH `classpath:db/migration` and this location to Flyway's
-- `locations` with `-target=21`, and only while no MONTHLY purchase has ever
-- been fulfilled -- once that stops being true, reverting requires a product
-- decision about those purchases, not a migration (design A1).
--
-- MySQL 8 has no SIGNAL outside a compound statement (stored procedure /
-- trigger), so the guard below aborts the script by letting InnoDB's own
-- CHECK constraint reject a bad INSERT -- no procedural SQL required. DDL in
-- MySQL auto-commits, so a failure here leaves the schema exactly as V20 left
-- it (plus the orphaned scratch table below, harmless and reusable/dropped by
-- hand) -- nothing destructive has run yet.
CREATE TABLE _rollback_guard (
    multi_session_purchases INT NOT NULL,
    CONSTRAINT chk_no_multi_session CHECK (multi_session_purchases = 0)
);

INSERT INTO _rollback_guard (multi_session_purchases)
SELECT COUNT(*) FROM (
    SELECT purchase_id FROM billing_purchase_sessions
    GROUP BY purchase_id HAVING COUNT(*) > 1
) multi;

-- Guard passed (zero multi-session purchases): rollback is safe to perform.
DROP TABLE _rollback_guard;

ALTER TABLE billing_purchases
    ADD COLUMN physical_session_id VARCHAR(64) NULL;

UPDATE billing_purchases bp
JOIN billing_purchase_sessions bps
    ON bps.purchase_id = bp.id AND bps.position = 0
SET bp.physical_session_id = bps.physical_session_id;

ALTER TABLE billing_purchases
    MODIFY COLUMN physical_session_id VARCHAR(64) NOT NULL;

DROP TABLE billing_purchase_sessions;
