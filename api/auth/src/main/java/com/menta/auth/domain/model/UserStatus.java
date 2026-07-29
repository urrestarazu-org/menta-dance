package com.menta.auth.domain.model;

/**
 * User account status.
 *
 * LOCKED indicates the account is administratively locked (e.g. abuse, dispute,
 * security compromise). Logins against a LOCKED account fail-closed by raising
 * LockedUserException and do not write to the outbox.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    LOCKED
}
