package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PaymentDetail;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link GetPaymentDetailUseCase}. */
@RequiredArgsConstructor
public class GetPaymentDetailUseCaseImpl implements GetPaymentDetailUseCase {

    private final AdminBillingApiClient adminBillingApiClient;

    @Override
    public PaymentDetail execute(String accessToken, String paymentId) {
        String token = ManualPaymentSupport.token(accessToken);
        return adminBillingApiClient.getPaymentDetail(token, ManualPaymentSupport.paymentId(paymentId));
    }
}
