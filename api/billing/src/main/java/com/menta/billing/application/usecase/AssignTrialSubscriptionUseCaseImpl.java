package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.AssignTrialCommand;
import com.menta.billing.application.dto.TrialAssignmentResult;
import com.menta.billing.application.port.in.AssignTrialSubscriptionUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.PlanNotAvailableException;
import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.exception.UserNotFoundException;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.TrialGrant;
import com.menta.shared.auth.UserExistencePort;
import java.time.Instant;
import java.util.UUID;

/**
 * Grants an admin-assigned trial subscription (US-BILLING-012).
 *
 * <p>Validation order is asserted, not incidental (design A5): admin guard → target user exists
 * (404, D8) → plan available (422) → no subscription already in force (409). {@code reason}/
 * {@code days} are validated ahead of all of this, at the web layer (Phase 4); {@link
 * TrialGrant}'s canonical constructor remains the last-resort guard here.</p>
 *
 * <p>Nothing is written until every check passes: {@link Subscription#trial} is the last call
 * before {@link SubscriptionRepository#saveNewSubscription}, which claims the slot and persists
 * the course snapshot in the same transaction (design A12).</p>
 */
public class AssignTrialSubscriptionUseCaseImpl implements AssignTrialSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserExistencePort userExistencePort;
    private final Clock clock;

    public AssignTrialSubscriptionUseCaseImpl(
        SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
        UserExistencePort userExistencePort, Clock clock
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.userExistencePort = userExistencePort;
        this.clock = clock;
    }

    @Override
    public TrialAssignmentResult assign(AssignTrialCommand command) {
        requireAdmin(command);

        if (!userExistencePort.existsById(command.userId())) {
            throw new UserNotFoundException();
        }

        Plan plan = planRepository.findActiveById(PlanId.of(command.planId()))
            .orElseThrow(PlanNotAvailableException::new);

        subscriptionRepository.findCurrentByUserId(command.userId()).ifPresent(current -> {
            throw new SubscriptionAlreadyActiveException(current.getEndDate().orElse(null));
        });

        Instant now = clock.now();
        TrialGrant grant = new TrialGrant(now, command.actingUserId(), command.reason(), command.days());
        Subscription trial = Subscription.trial(
            UUID.randomUUID(), command.userId(), plan.getId(), now, plan.courseIds(), grant
        );
        return TrialAssignmentResult.from(subscriptionRepository.saveNewSubscription(trial));
    }

    /**
     * Defense-in-depth (design A4): the route is already gated by {@code hasRole("ADMIN")}, so
     * reaching this with {@code isAdmin() == false} is unreachable in production. Collapses to
     * the same 404 shape as an unknown {@code userId} — same anti-oracle discipline {@link
     * CancelSubscriptionUseCaseImpl#resolveById} applies for a non-admin {@code ById} target.
     */
    private void requireAdmin(AssignTrialCommand command) {
        if (!command.isAdmin()) {
            throw new UserNotFoundException();
        }
    }
}
