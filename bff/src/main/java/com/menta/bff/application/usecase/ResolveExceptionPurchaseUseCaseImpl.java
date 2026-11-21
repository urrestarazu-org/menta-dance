package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PurchaseResolution;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException.Code;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link ResolveExceptionPurchaseUseCase}. */
@RequiredArgsConstructor
public class ResolveExceptionPurchaseUseCaseImpl implements ResolveExceptionPurchaseUseCase {

    private final AdminBillingApiClient adminBillingApiClient;

    @Override
    public void execute(String accessToken, String purchaseId, PurchaseResolution resolution, String note) {
        String token = ManualPaymentSupport.token(accessToken);
        String id = ManualPaymentSupport.uuid(purchaseId, Code.PURCHASE_NOT_FOUND);
        if (resolution == null) {
            throw new PaymentRefusalException(Code.INVALID_REQUEST);
        }
        adminBillingApiClient.resolvePurchase(token, id, resolution, ManualPaymentSupport.requiredText(note));
    }
}
