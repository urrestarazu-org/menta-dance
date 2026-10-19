package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.CorrectPaymentCommand;
import com.menta.billing.application.port.in.CorrectPaymentUseCase;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link CorrectPaymentUseCase} (#33, US-BILLING-005, design D9),
 * mirroring {@link TransactionalResolvePaymentProofUseCase}. Deliberately not {@code final} — same
 * CGLIB/{@code AopConfigException} precaution (#209).
 */
public class TransactionalCorrectPaymentUseCase implements CorrectPaymentUseCase {

    private final CorrectPaymentUseCase delegate;

    public TransactionalCorrectPaymentUseCase(CorrectPaymentUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void correct(CorrectPaymentCommand command) {
        delegate.correct(command);
    }
}
