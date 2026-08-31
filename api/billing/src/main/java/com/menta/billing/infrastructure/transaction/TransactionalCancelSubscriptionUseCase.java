package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationResult;
import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link CancelSubscriptionUseCase} (US-BILLING-011).
 *
 * <p>Cancellation is a single {@code Subscription} write today, but wrapping it keeps the same
 * discipline as {@code TransactionalCreateSubscriptionCheckoutUseCase}: a caller of this use
 * case never observes a partially applied cancellation.</p>
 */
public class TransactionalCancelSubscriptionUseCase implements CancelSubscriptionUseCase {

    private final CancelSubscriptionUseCase delegate;

    public TransactionalCancelSubscriptionUseCase(CancelSubscriptionUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public CancellationResult cancel(CancelSubscriptionCommand command) {
        return delegate.cancel(command);
    }
}
