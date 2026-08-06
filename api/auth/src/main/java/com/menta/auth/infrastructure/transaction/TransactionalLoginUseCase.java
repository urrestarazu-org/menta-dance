package com.menta.auth.infrastructure.transaction;

import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.in.LoginUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for LoginUseCase.
 *
 * Wraps the login use case with a database transaction to ensure atomicity
 * between refresh token persistence and outbox event creation (ADR-0027).
 *
 * This decorator is the ONLY input port bean exposed by AuthConfiguration.
 * Controllers invoke this proxied bean, never the raw implementation.
 */
public class TransactionalLoginUseCase implements LoginUseCase {

    private final LoginUseCase delegate;

    public TransactionalLoginUseCase(LoginUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public TokenPair execute(LoginCommand command) {
        return delegate.execute(command);
    }
}
