package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationResult;
import com.menta.billing.application.dto.CancellationTarget;
import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.SubscriptionNotFoundException;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;

/**
 * Cancels a subscription's auto-renewal (US-BILLING-011).
 *
 * <p>One authorization/transition path for both routes (design.md A4): {@link
 * CancellationTarget.Own} resolves the caller's own {@code ACTIVE} subscription; {@link
 * CancellationTarget.ById} is reachable only when {@code isAdmin} is {@code true} — a defensive
 * check independent of {@code SecurityConfig}'s own role gate on the admin route.</p>
 */
public class CancelSubscriptionUseCaseImpl implements CancelSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final Clock clock;

    public CancelSubscriptionUseCaseImpl(
        SubscriptionRepository subscriptionRepository, PlanRepository planRepository, Clock clock
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.clock = clock;
    }

    @Override
    public CancellationResult cancel(CancelSubscriptionCommand command) {
        // D1: the admin route always supplies a reason, checked before any lookup or write —
        // never partially validated after a subscription has already been read.
        if (command.target() instanceof CancellationTarget.ById && isBlank(command.reason())) {
            throw new IllegalArgumentException(
                "reason cannot be blank when cancelling on behalf of another user"
            );
        }

        Subscription subscription = resolve(command);
        Subscription cancelled = subscription.cancel(command.actingUserId(), command.reason(), clock.now());
        Subscription saved = subscriptionRepository.save(cancelled);

        // findById, not findActiveById: a plan the admin deactivated after this subscription
        // was sold must still resolve its cancellation policy text.
        Plan plan = planRepository.findById(saved.getPlanId())
            .orElseThrow(() -> new IllegalStateException("Plan not found for subscription " + saved.getId()));

        return new CancellationResult(
            saved.getId().toString(), saved.getStatus(), saved.getEndDate().orElse(null),
            plan.getCancellationPolicy()
        );
    }

    private Subscription resolve(CancelSubscriptionCommand command) {
        return switch (command.target()) {
            case CancellationTarget.Own ignored -> subscriptionRepository.findActiveByUserId(command.actingUserId())
                .orElseThrow(SubscriptionNotFoundException::new);
            case CancellationTarget.ById byId -> resolveById(command, byId);
        };
    }

    private Subscription resolveById(CancelSubscriptionCommand command, CancellationTarget.ById byId) {
        // Anti-oracle (design.md A5): a non-admin never learns whether the id even exists.
        if (!command.isAdmin()) {
            throw new SubscriptionNotFoundException();
        }
        return subscriptionRepository.findById(byId.subscriptionId())
            .filter(found -> found.getStatus() == SubscriptionStatus.ACTIVE)
            .orElseThrow(SubscriptionNotFoundException::new);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
