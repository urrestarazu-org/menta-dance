package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.AdminBillingApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link ApprovePaymentUseCase}. */
@RequiredArgsConstructor
public class ApprovePaymentUseCaseImpl implements ApprovePaymentUseCase {

    private final AdminBillingApiClient adminBillingApiClient;

    @Override
    public void execute(String accessToken, String paymentId) {
        String token = ManualPaymentSupport.token(accessToken);
        adminBillingApiClient.approvePayment(token, ManualPaymentSupport.paymentId(paymentId));
    }
}
