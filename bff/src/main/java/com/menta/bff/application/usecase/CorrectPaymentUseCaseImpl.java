package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CorrectionDecision;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException.Code;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link CorrectPaymentUseCase}. */
@RequiredArgsConstructor
public class CorrectPaymentUseCaseImpl implements CorrectPaymentUseCase {

    private final AdminBillingApiClient adminBillingApiClient;

    @Override
    public void execute(String accessToken, String paymentId, CorrectionDecision decision, String reason,
            String evidence) {
        String token = ManualPaymentSupport.token(accessToken);
        String id = ManualPaymentSupport.paymentId(paymentId);
        if (decision == null) {
            throw new PaymentRefusalException(Code.INVALID_REQUEST);
        }
        adminBillingApiClient.correctPayment(token, id, decision, ManualPaymentSupport.requiredText(reason),
                ManualPaymentSupport.requiredText(evidence));
    }
}
