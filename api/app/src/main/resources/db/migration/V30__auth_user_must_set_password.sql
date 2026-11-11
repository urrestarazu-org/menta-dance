-- V30 (#378): marker for accounts an administrator created whose holder has not chosen a password yet.
--
-- V29 is the head migration this builds on.
--
-- must_set_password = TRUE means the stored password_hash is a random, unusable placeholder (a different one per
-- account, never disclosed): nobody can authenticate with it, and activating the account requires the holder to
-- choose a password, which replaces the placeholder and clears the flag in the same transaction. There is no
-- default password for these accounts, ever.
--
-- NOT NULL DEFAULT FALSE: every existing account and every account created by public registration keeps its own
-- password and the activation flow it has today. There is no backfill.
ALTER TABLE auth_users
    ADD COLUMN must_set_password BOOLEAN NOT NULL DEFAULT FALSE AFTER demo;
