package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link CreateSubscriptionCheckoutUseCase}
 * (US-BILLING-010).
 *
 * <p>A checkout writes several rows — the {@code Payment}, the {@code
 * Subscription} claiming the user's slot, and the preference recorded on it —
 * and escenarios 3 and 4 demand all-or-nothing: a rejected checkout must leave
 * <em>no</em> local payment behind. The rollback is also what makes the
 * losing side of a concurrent race free of side effects, with no compensating
 * delete to get wrong.</p>
 */
public class TransactionalCreateSubscriptionCheckoutUseCase implements CreateSubscriptionCheckoutUseCase {

    private final CreateSubscriptionCheckoutUseCase delegate;

    public TransactionalCreateSubscriptionCheckoutUseCase(CreateSubscriptionCheckoutUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public SubscriptionCheckoutResult create(CreateSubscriptionCheckoutCommand command) {
        return delegate.create(command);
    }
}
