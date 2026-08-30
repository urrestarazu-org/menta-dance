package com.menta.billing.infrastructure.provider.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.domain.model.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LocalMercadoPagoPaymentStoreTest {

    private final LocalMercadoPagoPaymentStore store = new LocalMercadoPagoPaymentStore();

    @Test
    void creates_the_same_deterministic_preference_for_the_same_reference() {
        PaymentPreferenceRequest request = new PaymentPreferenceRequest(
            "payment-1", "Monthly plan", Money.of(new BigDecimal("1000.00"), "ARS")
        );

        assertThat(store.createPreference(request)).isEqualTo(store.createPreference(request));
    }

    @Test
    void returns_a_prepared_provider_payment() {
        ProviderPaymentResult expected = new ProviderPaymentResult(
            "approved", Money.of(new BigDecimal("1000.00"), "ARS"), "payment-1", "merchant-1"
        );
        store.registerPayment("provider-payment-1", expected);

        assertThat(store.fetchPayment("provider-payment-1")).isEqualTo(expected);
    }

    @Test
    void rejects_an_unknown_provider_payment() {
        assertThatThrownBy(() -> store.fetchPayment("missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown local Mercado Pago payment");
    }
}
