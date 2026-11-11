package com.menta.auth.infrastructure.transaction;

import com.menta.auth.application.dto.CreateUserCommand;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.in.CreateUserUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link CreateUserUseCase}.
 *
 * <p>The account, its activation token, the outbox event that sends the email and the audit entry must commit
 * together or not at all.
 */
public class TransactionalCreateUserUseCase implements CreateUserUseCase {

    private final CreateUserUseCase delegate;

    public TransactionalCreateUserUseCase(CreateUserUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public UserSummary create(CreateUserCommand command) {
        return delegate.create(command);
    }
}
