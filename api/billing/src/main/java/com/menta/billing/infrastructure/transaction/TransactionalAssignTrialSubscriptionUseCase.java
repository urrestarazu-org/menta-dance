package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.AssignTrialCommand;
import com.menta.billing.application.dto.TrialAssignmentResult;
import com.menta.billing.application.port.in.AssignTrialSubscriptionUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link AssignTrialSubscriptionUseCase} (US-BILLING-012).
 *
 * <p>Mirrors {@code TransactionalCancelSubscriptionUseCase}: the use case itself carries no
 * {@code @Transactional} annotation, so a caller of this decorator never observes a partially
 * applied trial grant — the slot claim and the course snapshot (design A12) must commit
 * together.</p>
 */
public class TransactionalAssignTrialSubscriptionUseCase implements AssignTrialSubscriptionUseCase {

    private final AssignTrialSubscriptionUseCase delegate;

    public TransactionalAssignTrialSubscriptionUseCase(AssignTrialSubscriptionUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public TrialAssignmentResult assign(AssignTrialCommand command) {
        return delegate.assign(command);
    }
}
