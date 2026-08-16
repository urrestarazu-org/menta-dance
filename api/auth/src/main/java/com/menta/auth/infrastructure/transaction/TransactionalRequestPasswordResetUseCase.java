package com.menta.auth.infrastructure.transaction;

import com.menta.auth.application.dto.RequestPasswordResetCommand;
import com.menta.auth.application.dto.RequestPasswordResetResult;
import com.menta.auth.application.port.in.RequestPasswordResetUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for RequestPasswordResetUseCase.
 *
 * <p>Makes token invalidation, fresh token issuance and outbox event creation
 * commit atomically: a crash between them would either strand an invalidated
 * token with no replacement, or persist a token whose delivery event never
 * exists (US-AUTH-005 escenario 3).</p>
 */
public class TransactionalRequestPasswordResetUseCase implements RequestPasswordResetUseCase {

    private final RequestPasswordResetUseCase delegate;

    public TransactionalRequestPasswordResetUseCase(RequestPasswordResetUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public RequestPasswordResetResult request(RequestPasswordResetCommand command) {
        return delegate.request(command);
    }
}
