package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CancelledSubscription;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import com.menta.bff.application.usecase.CancelSubscriptionResult.Outcome;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link CancelSubscriptionUseCase}: the Billing API owns every decision, this class only
 * turns its answers into outcomes. Log lines carry the exception class only, so neither the token nor an
 * upstream body can reach them.
 */
@Slf4j
@RequiredArgsConstructor
public class CancelSubscriptionUseCaseImpl implements CancelSubscriptionUseCase {

    private final SubscriptionApiClient subscriptionApiClient;
    private final ZoneId displayZone;

    @Override
    public CancelSubscriptionResult execute(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return CancelSubscriptionResult.of(Outcome.UNAVAILABLE);
        }
        try {
            CancelledSubscription cancelled = subscriptionApiClient.cancelSubscription(accessToken);
            return new CancelSubscriptionResult(
                    Outcome.CANCELLED, DateLabels.of(cancelled.accessEndsAt(), displayZone));
        } catch (SubscriptionApiClient.NoCancellableSubscriptionException e) {
            return CancelSubscriptionResult.of(Outcome.NOTHING_TO_CANCEL);
        } catch (SubscriptionApiClient.SubscriptionConflictException e) {
            return CancelSubscriptionResult.of(Outcome.CONFLICT);
        } catch (BillingApiClient.ServiceUnavailableException e) {
            log.warn("Subscription cancellation unavailable: {}", e.getClass().getSimpleName());
            return CancelSubscriptionResult.of(Outcome.UNAVAILABLE);
        }
    }
}
