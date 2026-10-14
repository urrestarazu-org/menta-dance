package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PaymentStatusView;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link GetPaymentProgressUseCase}: ownership is enforced upstream with a 404. */
@RequiredArgsConstructor
public class GetPaymentProgressUseCaseImpl implements GetPaymentProgressUseCase {

    private final PhysicalPurchaseApiClient purchaseApiClient;

    @Override
    public PaymentStatusView execute(String accessToken, String paymentId) {
        return purchaseApiClient.getPaymentProgress(accessToken, paymentId);
    }
}
