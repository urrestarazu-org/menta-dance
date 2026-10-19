package com.menta.billing.infrastructure.provider.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;
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
    void keeps_the_local_checkout_url_when_no_back_url_is_configured() {
        PaymentPreferenceRequest request = new PaymentPreferenceRequest(
            "payment-1", "Monthly plan", Money.of(new BigDecimal("1000.00"), "ARS")
        );

        assertThat(store.createPreference(request).checkoutUrl())
            .isEqualTo("http://local.mercadopago/checkout/local-preference-payment-1");
    }

    @Test
    void sends_the_buyer_to_the_configured_back_url_so_the_web_can_follow_it() {
        LocalMercadoPagoPaymentStore withBackUrl =
            new LocalMercadoPagoPaymentStore("http://localhost:8080/compras/retorno");
        PaymentPreferenceRequest request = new PaymentPreferenceRequest(
            "payment-1", "Monthly plan", Money.of(new BigDecimal("1000.00"), "ARS")
        );

        PaymentPreferenceResult first = withBackUrl.createPreference(request);

        assertThat(first.checkoutUrl()).isEqualTo("http://localhost:8080/compras/retorno");
        assertThat(first.preferenceId()).isEqualTo("local-preference-payment-1");
        assertThat(withBackUrl.createPreference(request)).isEqualTo(first);
    }

    @Test
    void treats_a_blank_back_url_as_not_configured() {
        LocalMercadoPagoPaymentStore blank = new LocalMercadoPagoPaymentStore("  ");
        PaymentPreferenceRequest request = new PaymentPreferenceRequest(
            "payment-1", "Monthly plan", Money.of(new BigDecimal("1000.00"), "ARS")
        );

        assertThat(blank.createPreference(request).checkoutUrl())
            .isEqualTo("http://local.mercadopago/checkout/local-preference-payment-1");
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
