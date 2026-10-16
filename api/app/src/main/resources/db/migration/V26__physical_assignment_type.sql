-- V26 (#521): records on every physical capacity assignment which kind of
-- purchase produced it (MONTHLY or INDIVIDUAL), so my-sessions can expose it.
--
-- V25 is the head migration this builds on.
--
-- LEGACY ASSUMPTION: rows that already exist were all created by the monthly
-- flow, because the individual-class purchase did not exist before this
-- change. They are therefore labelled MONTHLY through the temporary DEFAULT
-- below. If that assumption is ever wrong for an environment, correct those
-- rows by hand before relying on the value.
--
-- The DEFAULT is dropped right after the backfill so that the entity and the
-- API must always supply the type explicitly; an insert that omits it fails.
ALTER TABLE physical_capacity_assignments
    ADD COLUMN assignment_type VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' AFTER student_id;

ALTER TABLE physical_capacity_assignments
    ALTER COLUMN assignment_type DROP DEFAULT;
