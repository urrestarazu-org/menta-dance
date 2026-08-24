package com.menta.billing.application.usecase;

import com.menta.billing.application.port.in.MarkPurchaseExceptionPort;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.Reason;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * State-machine guard for the residual {@code EXCEPTION} terminal state
 * (proposal §4; design §4.2):
 *
 * <ul>
 *   <li>{@code PENDING_FULFILLMENT} → {@code EXCEPTION} — accepted, the
 *       spec scenario "Capacity invariant trips — Purchase flips to
 *       EXCEPTION".</li>
 *   <li>{@code EXCEPTION} → {@code EXCEPTION} — idempotent no-op (concurrent
 *       retry of the same handler call).</li>
 *   <li>{@code ASSIGNED} → {@code EXCEPTION} — refused; throws
 *       {@link IllegalPurchaseStateTransitionException}. Per ADR-0028
 *       §Decisión, once assigned, the residual path is no longer reachable.</li>
 * </ul>
 *
 * <p>The {@link Reason} parameter is recorded only via logs (the {@code
 * EXCEPTION} status itself doesn't carry a reason field), but exposing
 * the reason lets future improvements persist a {@code stringified reason}
 * column without breaking the call site.</p>
 */
@Component
public class MarkPurchaseExceptionUseCase implements MarkPurchaseExceptionPort {

    private final PurchaseRepository purchaseRepository;

    public MarkPurchaseExceptionUseCase(PurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markException(PaymentId paymentId, Reason reason) {
        Optional<Purchase> maybe = purchaseRepository.findByPaymentId(paymentId);
        if (maybe.isEmpty()) {
            throw new PaymentNotFoundException(paymentId);
        }
        Purchase purchase = maybe.get();
        if (purchase.getStatus() == FulfillmentStatus.ASSIGNED) {
            throw new IllegalPurchaseStateTransitionException(
                paymentId, FulfillmentStatus.ASSIGNED, FulfillmentStatus.EXCEPTION
            );
        }
        if (purchase.getStatus() == FulfillmentStatus.EXCEPTION) {
            return;
        }
        purchaseRepository.save(purchase.exception());
    }
}
