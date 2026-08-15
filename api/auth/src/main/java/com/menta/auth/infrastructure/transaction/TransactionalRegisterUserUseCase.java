package com.menta.auth.infrastructure.transaction;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for RegisterUserUseCase.
 *
 * Wraps the register use case with a database transaction to ensure
 * atomicity between user persistence, activation token issuance and outbox
 * event creation (auth-account-activation spec: "Registro pendiente y
 * entrega durable" — user, token and outbox event MUST commit together or
 * not at all).
 *
 * This decorator is the ONLY input port bean exposed by AuthConfiguration.
 * Controllers invoke this proxied bean, never the raw implementation.
 */
public class TransactionalRegisterUserUseCase implements RegisterUserUseCase {

    private final RegisterUserUseCase delegate;

    public TransactionalRegisterUserUseCase(RegisterUserUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public UserResult register(RegisterUserCommand command) {
        return delegate.register(command);
    }
}
