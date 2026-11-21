package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PaymentDetail;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link GetPaymentDetailUseCase}. */
@RequiredArgsConstructor
public class GetPaymentDetailUseCaseImpl implements GetPaymentDetailUseCase {

    private final AdminBillingApiClient adminBillingApiClient;
    private final ZoneId displayZone;

    @Override
    public PaymentVerificationView execute(String accessToken, String paymentId) {
        String token = ManualPaymentSupport.token(accessToken);
        PaymentDetail d = adminBillingApiClient.getPaymentDetail(token, ManualPaymentSupport.paymentId(paymentId));
        return new PaymentViewFactory(displayZone).of(d.paymentId(), d.userId(), d.targetModality(), d.amount(),
                d.currency(), d.statusType(), d.hasProof(), d.createdAt());
    }
}
