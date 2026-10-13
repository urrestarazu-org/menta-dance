package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.PurchaseResolutionResult;
import com.menta.billing.application.dto.ResolveExceptionPurchaseCommand;
import com.menta.billing.application.port.in.ResolveExceptionPurchaseUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link ResolveExceptionPurchaseUseCase} (#539). Deliberately not
 * {@code final} so Spring can proxy it, like every other {@code Transactional*UseCase}. The
 * purchase update and the {@code billing.PurchaseResolved} outbox append commit together.
 */
@RequiredArgsConstructor
public class TransactionalResolveExceptionPurchaseUseCase implements ResolveExceptionPurchaseUseCase {

    private final ResolveExceptionPurchaseUseCase delegate;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PurchaseResolutionResult resolve(ResolveExceptionPurchaseCommand command) {
        return delegate.resolve(command);
    }
}
