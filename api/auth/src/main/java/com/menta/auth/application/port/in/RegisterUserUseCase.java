package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;

/**
 * Port (interface) for registering a new user.
 * This is the entry point to the application layer.
 */
public interface RegisterUserUseCase {

    UserResult register(RegisterUserCommand command);
}
