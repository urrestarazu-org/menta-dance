package com.menta.billing.application.usecase;

import com.menta.billing.application.port.in.PurchaseCreationFromEventPort;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.PurchaseNotFoundException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.shared.billing.PaymentCompletedOutboxPayload;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent upsert for {@code Purchase(PENDING_FULFILLMENT)}, keyed on
 * {@code paymentId}. Mirrors design §5.4 exactly:
 *
 * <ol>
 *   <li>Read {@link PurchaseRepository#findByPaymentId(PaymentId)}.</li>
 *   <li>If present AND non-EXCEPTION → return existing; never save.</li>
 *   <li>If absent OR existing is EXCEPTION (recovery) → build
 *       {@link Purchase#pendingFulfillment(PaymentId, java.util.List)} from
 *       the caller-resolved {@code eligibleSessionIds} and save. The save
 *       never re-resurrects a settled purchase.
 *
 *       <p>{@code payload.targetReference()} is now the {@code quoteId}
 *       (design A5) and is no longer used to derive the session set here —
 *       a single reference cannot represent a {@code MONTHLY} purchase's N
 *       sessions. The caller resolves the concrete eligible-session list
 *       (Billing's {@code CoveragePlanner}, wired from {@code api:app}) and
 *       passes it explicitly.</p></li>
 *   <li>If save raises {@link DataIntegrityViolationException} against V8
 *       line 31 {@code uq_billing_purchases_payment_id} (UNIQUE collision
 *       with a concurrent handler) → re-fetch and return whatever is there.</li>
 * </ol>
 *
 * <p>"Read then conditional write" eliminates the classic TOCTOU window
 * by accepting the rare duplicate-insert race and re-reading on violation.</p>
 */
@Component
public class CreatePurchaseFromPaymentEventUseCase implements PurchaseCreationFromEventPort {

    private final PurchaseRepository purchaseRepository;

    public CreatePurchaseFromPaymentEventUseCase(PurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Purchase createPurchaseFromPaymentEvent(
        PaymentCompletedOutboxPayload payload,
        List<String> eligibleSessionIds
    ) {
        PaymentId paymentId = PaymentId.of(payload.paymentId());
        Optional<Purchase> existing = purchaseRepository.findByPaymentId(paymentId);
        if (existing.isPresent() && existing.get().getStatus() != FulfillmentStatus.EXCEPTION) {
            return existing.get();
        }
        Purchase pending = Purchase.pendingFulfillment(paymentId, eligibleSessionIds);
        try {
            return purchaseRepository.save(pending);
        } catch (DataIntegrityViolationException concurrentInsert) {
            // A peer handler inserted between our read and our save; whatever is
            // there now is the authoritative row, return it.
            return purchaseRepository.findByPaymentId(paymentId).orElseThrow(PurchaseNotFoundException::new);
        }
    }
}
