-- V29 (#378): append-only audit trail of administrative changes to users.
--
-- V28 is the head migration this builds on.
--
-- One row per change an administrator makes to an account. Today the only action is ROLE_CHANGED, with the role
-- before and after in previous_value / new_value; later actions reuse the same shape. actor_id is the
-- administrator who made the change, user_id the account it applies to, created_at the instant the application
-- decided (DATETIME(3), UTC), not the instant the row was written.
--
-- previous_value / new_value are VARCHAR(255) on purpose: they hold short enum names, never free text.
-- fk_auth_user_audit_user is kept, as in V23: accounts are never deleted by the application, and the FK prevents
-- orphan entries. actor_id has no FK: the trail must outlive whoever made the change.
--
-- No index beyond user_id: the table is small and read per user. The users listing (#378) searches by
-- contains-match over email and names, which no B-tree index would serve, so none is added to auth_users either.
CREATE TABLE auth_user_audit (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    action VARCHAR(50) NOT NULL,
    previous_value VARCHAR(255) NULL,
    new_value VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_auth_user_audit_user (user_id),
    CONSTRAINT fk_auth_user_audit_user
        FOREIGN KEY (user_id) REFERENCES auth_users (id)
);
