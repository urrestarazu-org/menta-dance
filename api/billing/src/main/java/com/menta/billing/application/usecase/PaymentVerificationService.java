package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCapacityAssignmentPort;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.application.port.out.VirtualAccessGrantPort;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.ProviderOutcome;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.Subscription;
import java.util.Optional;

/**
 * Verifies one {@code providerPaymentId} against Mercado Pago and applies
 * the resulting effect (US-BILLING-002). Called by {@code
 * WebhookVerificationWorker} inside its own per-row transaction — this class
 * has no transaction annotations of its own, it is plain application logic.
 *
 * <p>{@link PaymentProviderPort#fetchPayment} failures are NOT caught here —
 * they propagate to the worker, which retains the inbox row for retry with
 * backoff (ADR-0038). Only the fulfillment ports ({@link
 * PhysicalCapacityAssignmentPort}/{@link VirtualAccessGrantPort}) are caught,
 * because their failure degrades to {@code EXCEPTION} fulfillment, never to
 * an unconfirmed payment.</p>
 */
public final class PaymentVerificationService {

    private final PaymentRepository paymentRepository;
    private final PaymentProviderPort paymentProviderPort;
    private final PurchaseRepository purchaseRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PhysicalCapacityAssignmentPort physicalCapacityAssignmentPort;
    private final VirtualAccessGrantPort virtualAccessGrantPort;
    private final Clock clock;

    public PaymentVerificationService(
        PaymentRepository paymentRepository, PaymentProviderPort paymentProviderPort,
        PurchaseRepository purchaseRepository, SubscriptionRepository subscriptionRepository,
        PhysicalCapacityAssignmentPort physicalCapacityAssignmentPort,
        VirtualAccessGrantPort virtualAccessGrantPort, Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProviderPort = paymentProviderPort;
        this.purchaseRepository = purchaseRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.physicalCapacityAssignmentPort = physicalCapacityAssignmentPort;
        this.virtualAccessGrantPort = virtualAccessGrantPort;
        this.clock = clock;
    }

    public VerificationOutcome verify(String providerPaymentId) {
        Optional<Payment> maybePayment = paymentRepository.findByProviderPaymentId(providerPaymentId);
        if (maybePayment.isEmpty()) {
            return new VerificationOutcome.NoLocalPayment();
        }

        Payment payment = maybePayment.get();
        if (payment.getStatus() instanceof PaymentStatus.Completed) {
            // Idempotent under worker retry: a duplicate/late webhook for an
            // already-completed payment must not re-run or duplicate fulfillment.
            ensureFulfillment(payment);
            return new VerificationOutcome.Applied(payment);
        }
        if (payment.isTerminal()) {
            // Rejected/Cancelled/Expired never fulfill — nothing left to do.
            return new VerificationOutcome.Applied(payment);
        }

        ProviderPaymentResult result = paymentProviderPort.fetchPayment(providerPaymentId);
        ProviderOutcome outcome = new ProviderOutcome(
            result.providerStatus(), result.amount(), result.externalReference(), result.merchantAccountId()
        );
        Payment updated = paymentRepository.save(payment.applyProviderOutcome(outcome, clock.now()));
        if (updated.getStatus() instanceof PaymentStatus.Completed) {
            ensureFulfillment(updated);
        }
        return new VerificationOutcome.Applied(updated);
    }

    private void ensureFulfillment(Payment payment) {
        switch (payment.getTarget()) {
            case PaymentTarget.Physical physical -> ensurePurchase(payment, physical);
            case PaymentTarget.Virtual virtual -> ensureSubscription(payment, virtual);
        }
    }

    private void ensurePurchase(Payment payment, PaymentTarget.Physical physical) {
        if (purchaseRepository.findByPaymentId(payment.getId()).isPresent()) {
            return;
        }
        Purchase purchase = Purchase.pendingFulfillment(payment.getId(), physical.sessionId());
        try {
            physicalCapacityAssignmentPort.assign(physical.sessionId(), purchase.getId().toString());
            purchase = purchase.assigned();
        } catch (RuntimeException assignmentFailed) {
            purchase = purchase.exception();
        }
        purchaseRepository.save(purchase);
    }

    private void ensureSubscription(Payment payment, PaymentTarget.Virtual virtual) {
        if (subscriptionRepository.findByPaymentId(payment.getId()).isPresent()) {
            return;
        }
        Subscription subscription = Subscription.pendingFulfillment(payment.getId(), virtual.courseId());
        try {
            virtualAccessGrantPort.grant(virtual.courseId(), subscription.getId().toString());
            subscription = subscription.assigned();
        } catch (RuntimeException grantFailed) {
            subscription = subscription.exception();
        }
        subscriptionRepository.save(subscription);
    }
}
