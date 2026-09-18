package com.menta.billing.application.usecase;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import java.util.Optional;

/**
 * Activates or releases the fulfillment that follows a settled {@link Payment} (design C10).
 *
 * <p>Extracted <strong>unchanged</strong> from {@code PaymentVerificationService}'s former
 * private {@code ensureFulfillment}/{@code releaseFulfillment}/{@code ensureSubscription}
 * methods, so an approved bank-transfer payment (D1/P4) and the 72h expiry sweep (P5) activate or
 * cancel a subscription through exactly the same code an approved Mercado Pago payment already
 * does — never a second implementation of the course-snapshot freeze (escenario 2b).</p>
 *
 * <p>Deliberately not declared {@code final}: this codebase has already hit the CGLIB/{@code
 * AopConfigException} incident (#209) from a {@code final} class wrapped by a {@code
 * @Transactional} proxy, and nothing here forecloses this collaborator ever becoming one.</p>
 */
public class PaymentFulfillmentService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final Clock clock;
    private final PublishPhysicalPaymentCompletedUseCase publishPhysicalPaymentCompletedUseCase;

    public PaymentFulfillmentService(
        SubscriptionRepository subscriptionRepository, PlanRepository planRepository, Clock clock,
        PublishPhysicalPaymentCompletedUseCase publishPhysicalPaymentCompletedUseCase
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.clock = clock;
        this.publishPhysicalPaymentCompletedUseCase = publishPhysicalPaymentCompletedUseCase;
    }

    /** Formerly {@code PaymentVerificationService.ensureFulfillment} — unchanged. */
    public void ensure(Payment payment) {
        switch (payment.getTarget()) {
            case PaymentTarget.Physical ignored -> publishPhysicalPaymentCompletedUseCase.handle(payment);
            case PaymentTarget.Virtual virtual -> ensureSubscription(payment, virtual);
        }
    }

    /** Formerly {@code PaymentVerificationService.releaseFulfillment} — unchanged. */
    public void release(Payment payment) {
        if (payment.getTarget() instanceof PaymentTarget.Virtual) {
            subscriptionRepository.findByPaymentId(payment.getId())
                .filter(Subscription::occupiesUserSlot)
                .ifPresent(subscription -> subscriptionRepository.save(subscription.cancelled()));
        }
    }

    /**
     * Escenario 2: activate the subscription the checkout already created, freezing the plan's
     * courses as they stand at this instant. Escenario 2b follows from that snapshot: later
     * administrative edits to the plan cannot reach a subscription that already stored its own
     * list.
     *
     * <p>Reads the plan by id regardless of its status — the buyer paid while it was {@code
     * ACTIVE}, and a deactivation in the meantime must not quietly leave them with an empty
     * snapshot.</p>
     */
    private void ensureSubscription(Payment payment, PaymentTarget.Virtual virtual) {
        Optional<Subscription> existing = subscriptionRepository.findByPaymentId(payment.getId());
        if (existing.isEmpty()) {
            // Only the checkout creates virtual payments and writes both rows
            // in one transaction, so there is nothing to activate or invent.
            return;
        }

        if (existing.get().isActivated()) {
            if (!existing.get().grantsAccess()) {
                subscriptionRepository.save(existing.get().assigned());
            }
            return;
        }

        Optional<Plan> plan = planRepository.findById(PlanId.of(virtual.planId()));
        if (plan.isEmpty()) {
            subscriptionRepository.save(existing.get().exception());
            return;
        }

        Subscription activated = existing.get().activate(
            payment.confirmedAt().orElseGet(clock::now), plan.get().getDurationDays(), plan.get().courseIds()
        );
        subscriptionRepository.save(activated.assigned());
    }
}
