package com.menta.auth.infrastructure.transaction;

import com.menta.auth.application.dto.ResendActivationCommand;
import com.menta.auth.application.dto.ResendActivationResult;
import com.menta.auth.application.port.in.ResendActivationUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for ResendActivationUseCase.
 *
 * Wraps the resend use case with a database transaction to ensure atomicity
 * between token invalidation, fresh token issuance and outbox event
 * creation (auth-account-activation spec: "Reenvío no enumerativo" — los
 * tokens vigentes anteriores quedan invalidados y se emite un nuevo token y
 * evento durable en la misma operación).
 *
 * Wiring this bean through AuthConfiguration is deferred to task 3.4 — no
 * controller exists yet to invoke it.
 */
public class TransactionalResendActivationUseCase implements ResendActivationUseCase {

    private final ResendActivationUseCase delegate;

    public TransactionalResendActivationUseCase(ResendActivationUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public ResendActivationResult resend(ResendActivationCommand command) {
        return delegate.resend(command);
    }
}
