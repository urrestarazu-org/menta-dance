package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.dto.SubscriptionHistoryEntry;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetSubscriptionHistoryUseCase}.
 *
 * <p>Fails closed without propagating: a request without a token makes no call at all, and an
 * unavailable Billing API only replaces the section with a notice. The plan name is enrichment from
 * the public, token-less catalog (cached by its adapter), read only when there is something to name;
 * a catalog failure or a plan that left the catalog degrades the name to the generic label the
 * dashboard uses, never the history. Log lines carry the exception class only, so neither the token
 * nor an upstream body can reach them.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class GetSubscriptionHistoryUseCaseImpl implements GetSubscriptionHistoryUseCase {

    static final String NO_DATE = "Sin fecha";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SubscriptionApiClient subscriptionApiClient;
    private final BillingApiClient billingApiClient;
    private final ZoneId displayZone;

    @Override
    public SubscriptionHistoryView execute(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return new SubscriptionHistoryView(List.of(), true);
        }
        List<SubscriptionHistoryEntry> history;
        try {
            history = subscriptionApiClient.listSubscriptionHistory(accessToken);
        } catch (BillingApiClient.ServiceUnavailableException e) {
            log.warn("Subscription history unavailable, rendering the page without it: {}",
                    e.getClass().getSimpleName());
            return new SubscriptionHistoryView(List.of(), true);
        }
        Map<String, String> planNames = history.isEmpty() ? Map.of() : planNames();
        return new SubscriptionHistoryView(history.stream().map(entry -> toRow(entry, planNames)).toList(), false);
    }

    private SubscriptionHistoryView.Row toRow(SubscriptionHistoryEntry entry, Map<String, String> planNames) {
        return new SubscriptionHistoryView.Row(
                planNames.getOrDefault(entry.planId(), GetDashboardViewUseCaseImpl.GENERIC_PLAN_NAME),
                HistoryLabels.subscriptionStatus(entry.status()), HistoryLabels.subscriptionTone(entry.status()),
                entry.type() == SubscriptionHistoryEntry.Type.TRIAL, label(entry.startDate()),
                label(entry.endDate()));
    }

    private Map<String, String> planNames() {
        try {
            Map<String, String> names = new HashMap<>();
            for (PlanSummary plan : billingApiClient.getPlans()) {
                names.put(plan.id(), plan.name());
            }
            return names;
        } catch (BillingApiClient.NotFoundException | BillingApiClient.ServiceUnavailableException e) {
            log.warn("Plan catalog unavailable, showing generic plan names: {}", e.getClass().getSimpleName());
            return Map.of();
        }
    }

    private String label(Instant instant) {
        return instant == null ? NO_DATE : DATE.format(instant.atZone(displayZone));
    }
}
