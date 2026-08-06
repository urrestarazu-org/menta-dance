package com.menta.auth.infrastructure.transaction;

import com.menta.auth.application.dto.RefreshCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.in.RefreshTokenUseCase;
import com.menta.auth.domain.exception.RefreshTokenCompromisedException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for RefreshTokenUseCase.
 *
 * Wraps the refresh use case with a database transaction to ensure atomicity
 * between refresh rotation (USED → ACTIVE) and outbox event creation.
 *
 * This decorator is the ONLY input port bean exposed by AuthConfiguration.
 */
public class TransactionalRefreshTokenUseCase implements RefreshTokenUseCase {

    private final RefreshTokenUseCase delegate;

    public TransactionalRefreshTokenUseCase(RefreshTokenUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(noRollbackFor = RefreshTokenCompromisedException.class)
    public TokenPair execute(RefreshCommand command) {
        return delegate.execute(command);
    }
}
