package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.AdminBillingApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link RejectPaymentUseCase}. */
@RequiredArgsConstructor
public class RejectPaymentUseCaseImpl implements RejectPaymentUseCase {

    private final AdminBillingApiClient adminBillingApiClient;

    @Override
    public void execute(String accessToken, String paymentId, String reason) {
        String token = ManualPaymentSupport.token(accessToken);
        String id = ManualPaymentSupport.paymentId(paymentId);
        adminBillingApiClient.rejectPayment(token, id, ManualPaymentSupport.requiredText(reason));
    }
}
