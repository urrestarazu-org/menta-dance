package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PhysicalCheckout;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link PayPhysicalPurchaseUseCase}: the Billing API owns every decision. */
@RequiredArgsConstructor
public class PayPhysicalPurchaseUseCaseImpl implements PayPhysicalPurchaseUseCase {

    private final PhysicalPurchaseApiClient purchaseApiClient;

    @Override
    public PhysicalCheckout execute(String accessToken, String quoteId, String idempotencyKey) {
        return purchaseApiClient.createCheckout(accessToken, quoteId, idempotencyKey);
    }
}
