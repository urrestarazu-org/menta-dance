package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;

/**
 * Port (interface) for registering a new user.
 * This is the entry point to the application layer.
 */
public interface RegisterUserUseCase {

    /**
     * Registers a new account in the pending-activation state.
     *
     * <p>The account cannot log in until its activation token is consumed. Only the student role can be requested
     * through this public entry point.
     *
     * @param command carries the email, the password, the optional role and the client fingerprint used for throttling
     * @return the created account with its generated id and {@code PENDING_ACTIVATION} status
     */
    UserResult register(RegisterUserCommand command);
}
