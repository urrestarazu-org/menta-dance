package com.menta.auth.domain.exception;

import com.menta.auth.domain.model.UserId;
import com.menta.shared.domain.exceptions.ResourceNotFoundException;
import com.menta.shared.domain.vo.Email;

/**
 * Exception thrown when a user is not found.
 */
public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(UserId userId) {
        super("User", userId.getValue());
    }

    public UserNotFoundException(Email email) {
        super("User", email.getValue());
    }
}
