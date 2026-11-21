package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PaymentProof;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link GetPaymentProofUseCase}. */
@RequiredArgsConstructor
public class GetPaymentProofUseCaseImpl implements GetPaymentProofUseCase {

    private final AdminBillingApiClient adminBillingApiClient;

    @Override
    public PaymentProof execute(String accessToken, String paymentId) {
        String token = ManualPaymentSupport.token(accessToken);
        return adminBillingApiClient.fetchPaymentProof(token, ManualPaymentSupport.paymentId(paymentId));
    }
}
