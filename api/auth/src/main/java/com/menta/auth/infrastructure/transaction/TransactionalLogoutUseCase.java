package com.menta.auth.infrastructure.transaction;

import com.menta.auth.application.dto.LogoutCommand;
import com.menta.auth.application.port.in.LogoutUseCase;
import com.menta.auth.domain.exception.RefreshTokenCompromisedException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for LogoutUseCase.
 *
 * Wraps the logout use case with a database transaction to ensure atomicity
 * between refresh token revocation and outbox event creation.
 *
 * This decorator is the ONLY input port bean exposed by AuthConfiguration.
 */
public class TransactionalLogoutUseCase implements LogoutUseCase {

    private final LogoutUseCase delegate;

    public TransactionalLogoutUseCase(LogoutUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(noRollbackFor = RefreshTokenCompromisedException.class)
    public void execute(LogoutCommand command) {
        delegate.execute(command);
    }
}
