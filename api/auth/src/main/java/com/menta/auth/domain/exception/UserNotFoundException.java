package com.menta.auth.domain.exception;

import com.menta.auth.domain.model.UserId;
import com.menta.shared.domain.exceptions.BusinessException;

/** Thrown when an administrative operation targets a user that does not exist. */
public class UserNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "USER_NOT_FOUND";

    public UserNotFoundException(UserId userId) {
        super(ERROR_CODE, "User not found: " + userId.getValue());
    }
}
