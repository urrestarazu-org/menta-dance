package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PhysicalPurchaseBankTransferCheckoutResult;
import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;
import com.menta.billing.application.port.in.CreateBankTransferPhysicalPurchaseUseCase;
import com.menta.billing.application.port.in.CreatePhysicalPurchaseCheckoutUseCase;
import com.menta.billing.domain.model.PaymentMethod;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Design C3 (#36, US-BILLING-008): the router owns dispatch only — each delegate keeps its own
 * preconditions and behavior — mirroring {@link RoutingCreateSubscriptionCheckoutUseCase} exactly.
 * {@code PaymentMethod} has exactly two values, so the exhaustive {@code switch} inside {@link
 * RoutingCreatePhysicalPurchaseCheckoutUseCase} is itself the proof that "nothing else is routed".
 *
 * <p>Unlike the subscription router, the two delegates here do not share one return type: {@link
 * CreateBankTransferPhysicalPurchaseUseCase#create} returns {@code
 * PhysicalPurchaseBankTransferCheckoutResult} (Phase 3's bridge DTO), so the {@code BANK_TRANSFER}
 * arm converts it to {@code PhysicalPurchaseCheckoutResult} via {@code fromBankTransfer} at the
 * router's own boundary (C5 pulled forward into Phase 4 — see apply-progress).</p>
 */
class RoutingCreatePhysicalPurchaseCheckoutUseCaseTest {

    private CreatePhysicalPurchaseCheckoutUseCase mercadoPagoUseCase;
    private CreateBankTransferPhysicalPurchaseUseCase bankTransferUseCase;
    private RoutingCreatePhysicalPurchaseCheckoutUseCase router;

    @BeforeEach
    void setUp() {
        mercadoPagoUseCase = mock(CreatePhysicalPurchaseCheckoutUseCase.class);
        bankTransferUseCase = mock(CreateBankTransferPhysicalPurchaseUseCase.class);
        router = new RoutingCreatePhysicalPurchaseCheckoutUseCase(mercadoPagoUseCase, bankTransferUseCase);
    }

    private static CreatePhysicalPurchaseCheckoutCommand command(PaymentMethod method) {
        return new CreatePhysicalPurchaseCheckoutCommand(
            UUID.randomUUID(), UUID.randomUUID().toString(), method, "idem-1"
        );
    }

    @Test
    void dispatches_mercado_pago_to_the_checkout_pro_use_case_byte_identically() {
        CreatePhysicalPurchaseCheckoutCommand command = command(PaymentMethod.MERCADO_PAGO);
        PhysicalPurchaseCheckoutResult expected = mock(PhysicalPurchaseCheckoutResult.class);
        when(mercadoPagoUseCase.create(command)).thenReturn(expected);

        PhysicalPurchaseCheckoutResult result = router.create(command);

        assertThat(result).isSameAs(expected);
        verify(bankTransferUseCase, never()).create(any());
    }

    @Test
    void dispatches_bank_transfer_to_the_bank_transfer_use_case_and_converts_its_result() {
        CreatePhysicalPurchaseCheckoutCommand command = command(PaymentMethod.BANK_TRANSFER);
        PhysicalPurchaseBankTransferCheckoutResult bridgeResult = mock(PhysicalPurchaseBankTransferCheckoutResult.class);
        when(bankTransferUseCase.create(command)).thenReturn(bridgeResult);
        when(bridgeResult.paymentId()).thenReturn("pay-1");
        when(bridgeResult.quoteId()).thenReturn("quote-1");
        when(bridgeResult.status()).thenReturn("PENDING");
        when(bridgeResult.externalReference()).thenReturn("PHY-BT-abc");
        when(bridgeResult.bankTransferInstructions()).thenReturn(null);

        PhysicalPurchaseCheckoutResult result = router.create(command);

        assertThat(result.paymentId()).isEqualTo("pay-1");
        assertThat(result.quoteId()).isEqualTo("quote-1");
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.externalReference()).isEqualTo("PHY-BT-abc");
        assertThat(result.providerPreferenceId()).isNull();
        assertThat(result.checkoutUrl()).isNull();
        assertThat(result.bankTransferInstructions()).isNull();
        verify(mercadoPagoUseCase, never()).create(any());
    }
}
