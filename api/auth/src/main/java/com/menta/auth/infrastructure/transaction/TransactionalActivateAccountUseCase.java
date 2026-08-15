package com.menta.auth.infrastructure.transaction;

import com.menta.auth.application.dto.ActivateAccountCommand;
import com.menta.auth.application.port.in.ActivateAccountUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for ActivateAccountUseCase.
 *
 * Wraps the activate use case with a database transaction to ensure
 * atomicity between token consumption and user activation
 * (auth-account-activation design: "Flujo de activación" — UPDATE user
 * ACTIVE + token used_at MUST share a single COMMIT).
 *
 * Wiring this bean through AuthConfiguration is deferred to task 3.4 —
 * no controller exists yet to invoke it.
 */
public class TransactionalActivateAccountUseCase implements ActivateAccountUseCase {

    private final ActivateAccountUseCase delegate;

    public TransactionalActivateAccountUseCase(ActivateAccountUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void activate(ActivateAccountCommand command) {
        delegate.activate(command);
    }
}
