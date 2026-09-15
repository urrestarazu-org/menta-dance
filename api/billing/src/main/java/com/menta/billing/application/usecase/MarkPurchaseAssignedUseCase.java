package com.menta.billing.application.usecase;

import com.menta.billing.application.port.in.MarkPurchaseAssignedPort;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * State-machine guard for the successful {@code ASSIGNED} terminal state —
 * the mirror image of {@link MarkPurchaseExceptionUseCase}:
 *
 * <ul>
 *   <li>{@code PENDING_FULFILLMENT} → {@code ASSIGNED} — accepted; every
 *       eligible session was successfully claimed (design A3).</li>
 *   <li>{@code ASSIGNED} → {@code ASSIGNED} — idempotent no-op (a redelivered
 *       outbox event whose {@code assignAll} somehow succeeded again).</li>
 *   <li>{@code EXCEPTION} → {@code ASSIGNED} — refused; once the residual
 *       path settled, it never un-settles into a success (ADR-0028).</li>
 * </ul>
 */
@Component
public class MarkPurchaseAssignedUseCase implements MarkPurchaseAssignedPort {

    private final PurchaseRepository purchaseRepository;

    public MarkPurchaseAssignedUseCase(PurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markAssigned(PaymentId paymentId) {
        Optional<Purchase> maybe = purchaseRepository.findByPaymentId(paymentId);
        if (maybe.isEmpty()) {
            throw new PaymentNotFoundException(paymentId);
        }
        Purchase purchase = maybe.get();
        if (purchase.getStatus() == FulfillmentStatus.EXCEPTION) {
            throw new IllegalPurchaseStateTransitionException(
                paymentId, FulfillmentStatus.EXCEPTION, FulfillmentStatus.ASSIGNED
            );
        }
        if (purchase.getStatus() == FulfillmentStatus.ASSIGNED) {
            return;
        }
        purchaseRepository.save(purchase.assigned());
    }
}
