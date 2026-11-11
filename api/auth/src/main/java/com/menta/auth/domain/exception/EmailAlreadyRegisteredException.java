package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an administrator creates an account for an email that already has one. Unlike
 * {@link DuplicateRegistrationException}, which public registration swallows to avoid enumerating accounts, this
 * is raised on an admin-only path where the caller is entitled to know.
 */
public class EmailAlreadyRegisteredException extends BusinessException {

    private static final String ERROR_CODE = "EMAIL_ALREADY_REGISTERED";

    public EmailAlreadyRegisteredException() {
        super(ERROR_CODE, "An account with this email already exists");
    }
}
