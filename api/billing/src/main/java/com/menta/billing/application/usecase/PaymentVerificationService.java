package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.ProviderPaymentIdConflictException;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.ProviderOutcome;
import java.util.Optional;

/**
 * Verifies one {@code providerPaymentId} against Mercado Pago and applies
 * the resulting effect (US-BILLING-002). Called by {@code
 * WebhookVerificationWorker} inside its own per-row transaction — this class
 * has no transaction annotations of its own, it is plain application logic.
 *
 * <p>Two ways to reach the local {@code Payment}, in this order:</p>
 * <ol>
 *   <li><b>By {@code providerPaymentId}</b> — a payment already bound by an
 *       earlier webhook. Replays and late duplicates land here.</li>
 *   <li><b>By the provider's own {@code external_reference}</b> — a payment
 *       created by the checkout flow, which by construction has no provider
 *       id yet (see {@link Payment}). The reference is read from the
 *       <em>authenticated</em> provider response, never from the webhook
 *       payload: the webhook contributes a {@code data.id} and nothing else,
 *       so nothing unsigned ever selects which local payment gets settled.</li>
 * </ol>
 *
 * <p>The provider id is bound only after {@code matchesExpected} has passed,
 * and in the same transaction that applies the outcome — a mismatch must
 * never leave a payment carrying the id of a transaction it was not verified
 * against.</p>
 *
 * <p>{@link PaymentProviderPort#fetchPayment} failures are NOT caught here —
 * they propagate to the worker, which retains the inbox row for retry with
 * backoff (ADR-0038). Virtual fulfillment is local: the verified payment
 * activates Billing's subscription snapshot; Virtual queries that entitlement
 * later (ADR-0039). Physical fulfillment is orchestrated by {@code api:app}
 * under ADR-0028, not by this Billing worker.</p>
 */
public final class PaymentVerificationService {

    private final PaymentRepository paymentRepository;
    private final PaymentProviderPort paymentProviderPort;
    private final Clock clock;
    private final PaymentFulfillmentService paymentFulfillmentService;

    /**
     * Kept for backward compatibility with existing callers and tests: builds its own {@link
     * PaymentFulfillmentService} from the raw collaborators (design C10). Production wiring
     * should prefer {@link #PaymentVerificationService(PaymentRepository, PaymentProviderPort,
     * Clock, PaymentFulfillmentService)} so the exact same Spring-managed fulfillment
     * collaborator is shared with {@code ResolvePaymentProofUseCaseImpl} (P4) and {@code
     * PaymentExpiryWorker} (P5).
     */
    public PaymentVerificationService(
        PaymentRepository paymentRepository, PaymentProviderPort paymentProviderPort,
        SubscriptionRepository subscriptionRepository, PlanRepository planRepository, Clock clock,
        PublishPhysicalPaymentCompletedUseCase publishPhysicalPaymentCompletedUseCase
    ) {
        this(
            paymentRepository, paymentProviderPort, clock,
            new PaymentFulfillmentService(
                subscriptionRepository, planRepository, clock, publishPhysicalPaymentCompletedUseCase
            )
        );
    }

    public PaymentVerificationService(
        PaymentRepository paymentRepository, PaymentProviderPort paymentProviderPort, Clock clock,
        PaymentFulfillmentService paymentFulfillmentService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProviderPort = paymentProviderPort;
        this.clock = clock;
        this.paymentFulfillmentService = paymentFulfillmentService;
    }

    public VerificationOutcome verify(String providerPaymentId) {
        Optional<Payment> alreadyBound = paymentRepository.findByProviderPaymentId(providerPaymentId);
        if (alreadyBound.isPresent()) {
            return verifyBound(alreadyBound.get(), providerPaymentId);
        }
        return verifyByExternalReference(providerPaymentId);
    }

    /** The pre-existing path, unchanged: this payment already carries the provider's id. */
    private VerificationOutcome verifyBound(Payment payment, String providerPaymentId) {
        Optional<VerificationOutcome> shortCircuit = shortCircuitTerminal(payment);
        if (shortCircuit.isPresent()) {
            return shortCircuit.get();
        }
        return applyOutcome(payment, toOutcome(paymentProviderPort.fetchPayment(providerPaymentId)));
    }

    /**
     * The checkout path: ask the provider first, then use the reference it
     * returned — and only that — to find the local payment to bind.
     */
    private VerificationOutcome verifyByExternalReference(String providerPaymentId) {
        ProviderPaymentResult result = paymentProviderPort.fetchPayment(providerPaymentId);
        Optional<Payment> maybePayment = paymentRepository.findByExternalReference(result.externalReference());
        if (maybePayment.isEmpty()) {
            // US-BILLING-002: "sin inventar un Payment" — an unknown
            // reference becomes an administrative task referencing the
            // provider event, never a fabricated local payment.
            return new VerificationOutcome.NoLocalPayment();
        }

        Payment payment = maybePayment.get();
        Optional<VerificationOutcome> shortCircuit = shortCircuitTerminal(payment);
        if (shortCircuit.isPresent()) {
            return shortCircuit.get();
        }

        ProviderOutcome outcome = toOutcome(result);
        if (!payment.matchesExpected(outcome)) {
            // Never bind on a mismatch: applyProviderOutcome routes it to
            // ReconciliationRequired and the worker raises the task.
            return applyOutcome(payment, outcome);
        }

        Payment bound;
        try {
            bound = payment.bindProviderPaymentId(providerPaymentId);
        } catch (ProviderPaymentIdConflictException conflict) {
            // Two provider transactions on one local payment. The existing
            // binding stands; a human resolves it.
            return new VerificationOutcome.Applied(
                paymentRepository.save(payment.markReconciliationRequired(conflict.getMessage()))
            );
        }
        return applyOutcome(bound, outcome);
    }

    /**
     * Returns early when the payment already has a final financial outcome.
     *
     * <p>A completed payment still calls {@link PaymentFulfillmentService#ensure(Payment)}:
     * a duplicate or late webhook may arrive after the financial state was
     * persisted but before the virtual subscription snapshot was activated.
     * That operation is idempotent, so retrying it repairs this partial
     * failure without duplicating the entitlement.
     *
     * <p>Other terminal states ({@code Rejected}, {@code Cancelled}, and
     * {@code Expired}) have no fulfillment to recover and return immediately.
     * A pending status returns an empty result, allowing normal provider
     * verification to continue.
     */
    private Optional<VerificationOutcome> shortCircuitTerminal(Payment payment) {
        if (payment.getStatus() instanceof PaymentStatus.Completed) {
            // Idempotent under worker retry: a duplicate/late webhook for an
            // already-completed payment must not re-run or duplicate fulfillment.
            paymentFulfillmentService.ensure(payment);
            return Optional.of(new VerificationOutcome.Applied(payment));
        }
        if (payment.isTerminal()) {
            // Rejected/Cancelled/Expired never fulfill — nothing left to do.
            return Optional.of(new VerificationOutcome.Applied(payment));
        }
        return Optional.empty();
    }

    private VerificationOutcome applyOutcome(Payment payment, ProviderOutcome outcome) {
        Payment updated = paymentRepository.save(payment.applyProviderOutcome(outcome, clock.now()));
        if (updated.getStatus() instanceof PaymentStatus.Completed) {
            paymentFulfillmentService.ensure(updated);
        } else if (updated.isTerminal()) {
            // Escenario 6: rejected/cancelled/expired never activates the
            // subscription, and releases the user's slot so they can retry.
            paymentFulfillmentService.release(updated);
        }
        return new VerificationOutcome.Applied(updated);
    }

    private static ProviderOutcome toOutcome(ProviderPaymentResult result) {
        return new ProviderOutcome(
            result.providerStatus(), result.amount(), result.externalReference(), result.merchantAccountId()
        );
    }
}
