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
 *       passes it explicitly.</p>
 *
 *       <p>#238: an empty {@code eligibleSessionIds} means the payment never
 *       resolved to any schedulable session in the first place (missing
 *       payment/target, unresolvable quote, coverage shortfall) — there is
 *       no legitimate {@code PENDING_FULFILLMENT} to record for it. Building
 *       via {@link Purchase#pendingFulfillment} in that case would hit the
 *       domain invariant that forbids an empty session list for any status
 *       other than {@code EXCEPTION}. So an empty list builds directly via
 *       {@link Purchase#exception(PaymentId, java.util.List)} instead —
 *       skipping the intermediate state entirely.</p></li>
 *   <li>Save via {@link PurchaseRepository#saveIsolated(Purchase)} (#245),
 *       never {@link PurchaseRepository#save(Purchase)} directly. This
 *       method's own {@code @Transactional(REQUIRED)} always joins an
 *       already-open ambient transaction in production (the outbox worker's
 *       {@code REQUIRES_NEW}); a save-then-catch here against the V8 line 31
 *       {@code uq_billing_purchases_payment_id} UNIQUE collision cannot
 *       reliably recover, because Spring's AOP advice marks that ambient
 *       transaction rollback-only the instant the constraint violation
 *       escapes {@code save(Purchase)}'s own {@code MANDATORY} proxy —
 *       regardless of a caller catching it afterward — so the whole method
 *       fails at commit with {@code UnexpectedRollbackException} even though
 *       the exception was "handled". This is the exact proxy-boundary
 *       mechanic already fixed once in this package for {@code
 *       PublishPhysicalPaymentCompletedUseCase} (#242) and in {@code
 *       WebhookInboxAppenderAdapter}. {@code saveIsolated} isolates the risky
 *       insert in its own {@code REQUIRES_NEW} transaction, so a lost race
 *       there rolls back and closes before this method ever sees it.</li>
 *   <li>If {@code saveIsolated} returns empty (lost the race) → re-fetch via
 *       {@link PurchaseRepository#findByPaymentIdForUpdate(PaymentId)}, a
 *       LOCKING read, and return whatever is there. A plain {@code
 *       findByPaymentId} here would risk missing the winner's row: under
 *       REPEATABLE READ, this method's own step 1 read may already have
 *       fixed this transaction's MVCC snapshot before the winner's {@code
 *       saveIsolated} committed on its own connection.</li>
 * </ol>
 *
 * <p>"Read then conditional write" eliminates the classic TOCTOU window by
 * accepting the rare duplicate-insert race and re-reading when {@code
 * saveIsolated} reports it lost.</p>
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
        Purchase toSave = eligibleSessionIds.isEmpty()
            ? Purchase.exception(paymentId, eligibleSessionIds)
            : Purchase.pendingFulfillment(paymentId, eligibleSessionIds);
        return purchaseRepository.saveIsolated(toSave)
            .orElseGet(() ->
                purchaseRepository.findByPaymentIdForUpdate(paymentId).orElseThrow(PurchaseNotFoundException::new)
            );
    }
}
