package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CurrentSubscription;
import com.menta.bff.application.dto.DashboardView;
import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetDashboardViewUseCase}.
 *
 * <p>Unlike the plans page, nothing here fails closed by propagating: the dashboard offers no
 * action that depends on the state, so an unverifiable subscription only replaces the card with a
 * notice. The plan name is enrichment from the public, token-less catalog; it is read only when the
 * state names a plan, and any catalog failure degrades the name, never the state. Log lines carry
 * the exception class only, so neither the token nor an upstream body can reach them.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class GetDashboardViewUseCaseImpl implements GetDashboardViewUseCase {

    static final String GENERIC_PLAN_NAME = "Tu plan";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BillingApiClient billingApiClient;
    private final SubscriptionApiClient subscriptionApiClient;
    private final ZoneId displayZone;

    @Override
    public DashboardView execute(String accessToken) {
        CurrentSubscription current;
        try {
            current = subscriptionApiClient.getCurrentSubscription(accessToken);
        } catch (BillingApiClient.ServiceUnavailableException e) {
            log.warn("Subscription state unavailable, rendering the dashboard without it: {}",
                    e.getClass().getSimpleName());
            return new DashboardView.Unavailable();
        }
        return switch (current) {
            case CurrentSubscription.None none -> new DashboardView.None();
            case CurrentSubscription.Active active -> new DashboardView.Active(
                    planName(active.planId()), label(active.endDate()),
                    remainingLabel(active.daysRemaining()), active.expiringSoon());
            case CurrentSubscription.Pending pending -> new DashboardView.Pending(planName(pending.planId()));
            case CurrentSubscription.Expired expired -> new DashboardView.Expired(
                    planName(expired.planId()), label(expired.endDate()));
        };
    }

    private String planName(String planId) {
        if (planId == null) {
            return GENERIC_PLAN_NAME;
        }
        try {
            return billingApiClient.getPlans().stream()
                    .filter(plan -> planId.equals(plan.id()))
                    .map(PlanSummary::name)
                    .findFirst()
                    .orElse(GENERIC_PLAN_NAME);
        } catch (BillingApiClient.NotFoundException | BillingApiClient.ServiceUnavailableException e) {
            log.warn("Plan catalog unavailable, showing a generic plan name: {}", e.getClass().getSimpleName());
            return GENERIC_PLAN_NAME;
        }
    }

    private static String remainingLabel(Long daysRemaining) {
        if (daysRemaining == null) {
            return null;
        }
        if (daysRemaining == 0) {
            return "Vence hoy";
        }
        return daysRemaining == 1 ? "Vence en 1 día" : "Vence en " + daysRemaining + " días";
    }

    private String label(Instant instant) {
        return DATE.format(instant.atZone(displayZone));
    }
}
