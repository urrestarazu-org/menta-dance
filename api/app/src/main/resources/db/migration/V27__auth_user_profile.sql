-- V27 (#652): personal data collected at registration, stored on auth_users.
--
-- V26 is the head migration this builds on.
--
-- All four columns are NULLable on purpose: accounts that already exist
-- (admin, demo, E2E fixtures and any earlier registration) have no profile and
-- keep working unchanged. Only the registration flow fills them in.
--   first_name, last_name: required by the API at registration, max 100 chars.
--   phone: E.164 (a plus sign and up to 15 digits), validated by the API.
--   birth_date: optional, a calendar date without time zone.
--
-- PII: this is personal data. Never log these values or copy them into
-- fixtures; test data must be obviously fake.
ALTER TABLE auth_users
    ADD COLUMN first_name VARCHAR(100) NULL AFTER status,
    ADD COLUMN last_name VARCHAR(100) NULL AFTER first_name,
    ADD COLUMN phone VARCHAR(20) NULL AFTER last_name,
    ADD COLUMN birth_date DATE NULL AFTER phone;
