package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.in.CreateBankTransferSubscriptionUseCase;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PlanId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Design D3: the router owns dispatch only — each delegate keeps its own preconditions and
 * behavior. {@code PaymentMethod} has exactly two values, so the exhaustive {@code switch} inside
 * {@link RoutingCreateSubscriptionCheckoutUseCase} is itself the proof that "nothing else is
 * routed" — a third method could not compile without a matching branch.
 */
class RoutingCreateSubscriptionCheckoutUseCaseTest {

    private CreateSubscriptionCheckoutUseCase mercadoPagoUseCase;
    private CreateBankTransferSubscriptionUseCase bankTransferUseCase;
    private RoutingCreateSubscriptionCheckoutUseCase router;

    @BeforeEach
    void setUp() {
        mercadoPagoUseCase = mock(CreateSubscriptionCheckoutUseCase.class);
        bankTransferUseCase = mock(CreateBankTransferSubscriptionUseCase.class);
        router = new RoutingCreateSubscriptionCheckoutUseCase(mercadoPagoUseCase, bankTransferUseCase);
    }

    private static CreateSubscriptionCheckoutCommand command(PaymentMethod method) {
        return new CreateSubscriptionCheckoutCommand(
            UUID.randomUUID(), PlanId.generate().toString(), method, "idem-1"
        );
    }

    @Test
    void dispatches_mercado_pago_to_the_checkout_pro_use_case_byte_identically() {
        CreateSubscriptionCheckoutCommand command = command(PaymentMethod.MERCADO_PAGO);
        SubscriptionCheckoutResult expected = mock(SubscriptionCheckoutResult.class);
        when(mercadoPagoUseCase.create(command)).thenReturn(expected);

        SubscriptionCheckoutResult result = router.create(command);

        assertThat(result).isSameAs(expected);
        verify(bankTransferUseCase, never()).create(any());
    }

    @Test
    void dispatches_bank_transfer_to_the_bank_transfer_use_case() {
        CreateSubscriptionCheckoutCommand command = command(PaymentMethod.BANK_TRANSFER);
        SubscriptionCheckoutResult expected = mock(SubscriptionCheckoutResult.class);
        when(bankTransferUseCase.create(command)).thenReturn(expected);

        SubscriptionCheckoutResult result = router.create(command);

        assertThat(result).isSameAs(expected);
        verify(mercadoPagoUseCase, never()).create(any());
    }
}
