package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.PurchaseResolutionResult;
import com.menta.billing.application.dto.ResolveExceptionPurchaseCommand;

/** Admin closes a purchase stuck in {@code EXCEPTION} as {@code REFUNDED} or {@code COMPENSATED}. */
public interface ResolveExceptionPurchaseUseCase {

    /**
     * Resolves the purchase and publishes {@code billing.PurchaseResolved}, touching nothing else.
     *
     * <p>Fails with {@code PurchaseNotFoundException} for an unknown purchase, with {@code
     * IllegalPurchaseStateTransitionException} when it is not in {@code EXCEPTION} (and not already
     * resolved) and with {@code PurchaseResolutionConflictException} when it was resolved
     * differently. Repeating the identical resolution and note is a no-op that publishes nothing.
     */
    PurchaseResolutionResult resolve(ResolveExceptionPurchaseCommand command);
}
