package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.ResolvePaymentProofCommand;
import com.menta.billing.application.port.in.ResolvePaymentProofUseCase;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link ResolvePaymentProofUseCase} (#31, US-BILLING-003). Deliberately
 * not {@code final} — a {@code final} bean here reproduces the CGLIB {@code AopConfigException}
 * incident already fixed once in this change.
 */
public class TransactionalResolvePaymentProofUseCase implements ResolvePaymentProofUseCase {

    private final ResolvePaymentProofUseCase delegate;

    public TransactionalResolvePaymentProofUseCase(ResolvePaymentProofUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void resolve(ResolvePaymentProofCommand command) {
        delegate.resolve(command);
    }
}
