package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.SubscriptionCheckout;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link SubscribeToPlanUseCase}: the Billing API owns every decision. */
@RequiredArgsConstructor
public class SubscribeToPlanUseCaseImpl implements SubscribeToPlanUseCase {

    private final SubscriptionApiClient subscriptionApiClient;

    @Override
    public SubscriptionCheckout execute(String accessToken, String planId, String idempotencyKey) {
        return subscriptionApiClient.createSubscriptionCheckout(accessToken, planId, idempotencyKey);
    }
}
