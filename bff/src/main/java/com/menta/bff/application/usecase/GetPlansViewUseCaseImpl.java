package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CurrentSubscription;
import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.dto.PlansOffer;
import com.menta.bff.application.dto.PlansOffer.OfferNotice;
import com.menta.bff.application.dto.PlansOffer.SubscribeAction;
import com.menta.bff.application.dto.PlansView;
import com.menta.bff.application.dto.PlansViewer;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link GetPlansViewUseCase}.
 *
 * <p>The catalog call is shared by every viewer and carries no token. The subscription call
 * happens only for a student and its failures are not caught: an unverifiable state must not
 * leave a way to start a checkout.
 * </p>
 */
@RequiredArgsConstructor
public class GetPlansViewUseCaseImpl implements GetPlansViewUseCase {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BillingApiClient billingApiClient;
    private final SubscriptionApiClient subscriptionApiClient;
    private final ZoneId displayZone;
    private final Supplier<String> idempotencyKeys;

    @Override
    public PlansView execute(PlansViewer viewer, String accessToken) {
        List<PlanSummary> plans = billingApiClient.getPlans();
        PlansOffer offer = switch (viewer) {
            case ANONYMOUS -> offer(SubscribeAction.LOGIN, OfferNotice.NONE, null, null);
            case OTHER -> offer(SubscribeAction.NONE, OfferNotice.NONE, null, null);
            case STUDENT -> studentOffer(plans, subscriptionApiClient.getCurrentSubscription(accessToken));
        };
        return new PlansView(plans, offer);
    }

    private PlansOffer studentOffer(List<PlanSummary> plans, CurrentSubscription current) {
        return switch (current) {
            case CurrentSubscription.None none -> subscribable(plans, OfferNotice.NONE, null);
            case CurrentSubscription.Expired expired -> subscribable(
                plans, OfferNotice.EXPIRED, label(expired.endDate()));
            case CurrentSubscription.Active active -> offer(
                SubscribeAction.NONE, OfferNotice.ACTIVE, label(active.endDate()), null);
            case CurrentSubscription.Pending pending -> offer(
                SubscribeAction.NONE, OfferNotice.PENDING, null, pending.subscriptionId());
        };
    }

    private PlansOffer subscribable(List<PlanSummary> plans, OfferNotice notice, String endDateLabel) {
        Map<String, String> keys = new LinkedHashMap<>();
        plans.forEach(plan -> keys.put(plan.id(), idempotencyKeys.get()));
        return new PlansOffer(SubscribeAction.SUBSCRIBE, notice, endDateLabel, null, keys);
    }

    private static PlansOffer offer(
            SubscribeAction action, OfferNotice notice, String endDateLabel, String subscriptionId) {
        return new PlansOffer(action, notice, endDateLabel, subscriptionId, Map.of());
    }

    private String label(Instant instant) {
        return DATE.format(instant.atZone(displayZone));
    }
}
