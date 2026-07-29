package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

import java.util.UUID;

/**
 * Thrown when login is attempted against a user account in LOCKED status.
 *
 * The login flow MUST NOT emit any outbox event and MUST NOT issue tokens; the
 * caller is expected to map this to HTTP 423 Locked.
 */
public class LockedUserException extends BusinessException {

    private static final String ERROR_CODE = "USER_LOCKED";

    public LockedUserException(UUID userId) {
        super(ERROR_CODE, "User account is locked: " + userId);
    }
}
