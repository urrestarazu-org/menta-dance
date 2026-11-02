-- V28 (#698): explicit marker for demo/test accounts on auth_users.
--
-- V27 is the head migration this builds on.
--
-- demo = TRUE means the account is not a real customer: no outgoing email is
-- addressed to it (billing notifications, activation, password reset). The
-- flag is deliberately NOT derived from the email domain or the role.
--
-- NOT NULL DEFAULT FALSE: every existing and every newly registered account is a
-- real one unless someone says otherwise. There is no backfill and no email is
-- hardcoded in any migration: databases seeded with the demo profile before V28
-- must be recreated (the demo profile then creates its users already flagged), or
-- flagged by hand with the SQL in docs/32-VPS-DONWEB-RUNBOOK.md section 2.13.
ALTER TABLE auth_users
    ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE AFTER token_version;
