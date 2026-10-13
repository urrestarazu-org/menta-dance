package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.PaymentProofFile;
import com.menta.bff.application.dto.PhysicalBankTransfer;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PayPhysicalPurchaseByTransferUseCase")
class PayPhysicalPurchaseByTransferUseCaseImplTest {

    private final PhysicalPurchaseApiClient api = mock(PhysicalPurchaseApiClient.class);
    private final PayPhysicalPurchaseByTransferUseCase useCase = new PayPhysicalPurchaseByTransferUseCaseImpl(api);

    @Test
    @DisplayName("starting a transfer words the amount in Spanish and keeps the upstream data")
    void start() {
        when(api.createBankTransferCheckout("token", "quote-1", "key-1")).thenReturn(new PhysicalBankTransfer(
                "pay-1", "cbu", "alias", "holder", "cuit", new BigDecimal("12500.50"), "ARS", "PHY-BT-1"));

        BankTransferInstructionsView view = useCase.start("token", "quote-1", "key-1");

        assertThat(view.paymentId()).isEqualTo("pay-1");
        assertThat(view.amountLabel()).contains("12.500,50");
        assertThat(view.reference()).isEqualTo("PHY-BT-1");
        assertThat(view.cbu()).isEqualTo("cbu");
    }

    @Test
    @DisplayName("a proof goes to the port untouched")
    void submitProof() {
        PaymentProofFile proof = new PaymentProofFile("r.png", "image/png", new byte[] {1});

        useCase.submitProof("token", "pay-1", proof);

        verify(api).submitPaymentProof("token", "pay-1", proof);
    }

    @Test
    @DisplayName("the transfer idempotency key differs from the card one and is stable")
    void transferKey() {
        PurchaseQuoteView view = new PurchaseQuoteView("q", "c", "t", "m", "a", 1, null, "e", false, "key-1");

        assertThat(view.transferIdempotencyKey()).isEqualTo("key-1-transfer").isNotEqualTo(view.idempotencyKey());
    }
}
