package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PaymentProofFile;
import com.menta.bff.application.dto.PhysicalBankTransfer;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link PayPhysicalPurchaseByTransferUseCase}: it words the amount, nothing else. */
@RequiredArgsConstructor
public class PayPhysicalPurchaseByTransferUseCaseImpl implements PayPhysicalPurchaseByTransferUseCase {

    private final PhysicalPurchaseApiClient purchaseApiClient;

    @Override
    public BankTransferInstructionsView start(String accessToken, String quoteId, String idempotencyKey) {
        PhysicalBankTransfer transfer = purchaseApiClient.createBankTransferCheckout(
                accessToken, quoteId, idempotencyKey);
        return new BankTransferInstructionsView(transfer.paymentId(), transfer.cbu(), transfer.alias(),
                transfer.holder(), transfer.cuit(), ClassLabels.amount(transfer.amount(), transfer.currency()),
                transfer.reference());
    }

    @Override
    public void submitProof(String accessToken, String paymentId, PaymentProofFile proof) {
        purchaseApiClient.submitPaymentProof(accessToken, paymentId, proof);
    }
}
