package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;
import com.menta.billing.application.port.in.CreatePhysicalPurchaseCheckoutUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link CreatePhysicalPurchaseCheckoutUseCase}
 * (#41, US-PHYSICAL-004) — mirrors {@link TransactionalCreateSubscriptionCheckoutUseCase}
 * exactly.
 *
 * <p>{@link com.menta.billing.application.port.out.PaymentRepository#save} is
 * {@code @Transactional(propagation = Propagation.MANDATORY)}: it requires an
 * already-open transaction and throws {@code IllegalTransactionStateException}
 * otherwise. {@link com.menta.billing.application.usecase.CreatePhysicalPurchaseCheckoutUseCaseImpl}
 * is a plain POJO with no transaction boundary of its own — exactly like
 * {@code CreateSubscriptionCheckoutUseCaseImpl} — so it must be wrapped by a
 * decorator carrying {@link Transactional}, never called directly as the bean
 * exposed to the controller. A unit test with a mocked repository never
 * exercises the real {@code MANDATORY}-propagation proxy and cannot catch a
 * missing wrapper; only a real Spring context does.</p>
 */
public class TransactionalCreatePhysicalPurchaseCheckoutUseCase implements CreatePhysicalPurchaseCheckoutUseCase {

    private final CreatePhysicalPurchaseCheckoutUseCase delegate;

    public TransactionalCreatePhysicalPurchaseCheckoutUseCase(CreatePhysicalPurchaseCheckoutUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public PhysicalPurchaseCheckoutResult create(CreatePhysicalPurchaseCheckoutCommand command) {
        return delegate.create(command);
    }
}
