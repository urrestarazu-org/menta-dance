package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.CreateUserCommand;
import com.menta.auth.application.dto.UserSummary;

/** Application-layer entry point for an administrator creating a staff account. */
public interface CreateUserUseCase {

    /**
     * Creates a pending account that its holder activates by email and where they choose their own password.
     *
     * <p>The account has an unusable password, so it cannot be used to log in until it is activated. The creation,
     * the activation email and the audit entry succeed or fail together.
     *
     * @return the created account, in {@code PENDING_ACTIVATION}
     * @throws IllegalArgumentException when the email or a name is invalid, or the role is not a staff role
     * @throws com.menta.auth.domain.exception.EmailAlreadyRegisteredException when the email already has an account
     */
    UserSummary create(CreateUserCommand command);
}
