-- V20.2 (compensating, NOT part of normal rollout -- design.md "Migration /
-- Rollout"): drops payment_id and converted_at from physical_capacity_holds
-- (V20.1). The table has zero production rows by construction, so this is a
-- pure schema revert -- there is no data to preserve and no guard is needed
-- (contrast V21__revert_billing_purchase_sessions.sql, whose guard exists
-- only because that revert is lossy for multi-session purchases).
--
-- Numbered V20.2, not the V22 design.md sketches, for the same reason V20.1
-- replaces V21 as this change's forward migration's number: it sits right
-- after V20.1 and strictly before the pre-existing V21 revert, so applying
-- it never touches or reorders that unrelated script.
--
-- Deliberately NOT under classpath:db/migration (spring.flyway.locations,
-- api/app/src/main/resources/application.yml): the application, and every
-- integration test that drives Flyway against that same location, would
-- apply this automatically on the very next "migrate to latest" right after
-- V20.1 -- reverting the feature this whole change ships before it ever
-- runs. This file is an operator-invoked escape hatch only, applied by
-- explicitly adding BOTH classpath:db/migration and this location to
-- Flyway's locations with -target=20.2.
ALTER TABLE physical_capacity_holds
    DROP KEY idx_physical_holds_payment,
    DROP KEY uq_physical_holds_payment_session,
    DROP COLUMN converted_at,
    DROP COLUMN payment_id;
