package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.AssignTrialCommand;
import com.menta.billing.application.dto.TrialAssignmentResult;

/**
 * Application-layer entry point for an admin-assigned trial subscription (US-BILLING-012).
 * Single path, admin-only — unlike {@link CancelSubscriptionUseCase}, no self-service target
 * exists (design.md A4).
 */
public interface AssignTrialSubscriptionUseCase {

    /**
     * Validation order is asserted, not incidental (design A5): admin guard → target user
     * exists (404, D8) → plan available (422) → no subscription already in force (409).
     *
     * @throws com.menta.billing.domain.exception.UserNotFoundException if the caller is not an
     *     admin, or the target {@code userId} does not reference an existing user — both
     *     collapse to the same 404 shape (anti-oracle, design A4/A5)
     * @throws com.menta.billing.domain.exception.PlanNotAvailableException if the plan does not
     *     exist or is not {@code ACTIVE}
     * @throws com.menta.billing.domain.exception.SubscriptionAlreadyActiveException if the
     *     target user already has a subscription in force ({@code ACTIVE} or {@code PENDING})
     */
    TrialAssignmentResult assign(AssignTrialCommand command);
}
