package com.menta.billing.infrastructure.provider.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.domain.model.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalMercadoPagoPaymentStoreWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(LocalMercadoPagoPaymentStore.class)
        .withInitializer(context -> context.getEnvironment().addActiveProfile("e2e-mercadopago"));

    private static final PaymentPreferenceRequest REQUEST = new PaymentPreferenceRequest(
        "payment-1", "Monthly plan", Money.of(new BigDecimal("1000.00"), "ARS")
    );

    @Test
    void reads_the_back_url_from_the_billing_property() {
        runner.withPropertyValues("billing.mercadopago.checkout-back-url=http://localhost:8080/compras/retorno")
            .run(context -> assertThat(context.getBean(LocalMercadoPagoPaymentStore.class)
                .createPreference(REQUEST).checkoutUrl())
                .isEqualTo("http://localhost:8080/compras/retorno"));
    }

    @Test
    void falls_back_to_the_local_url_when_the_property_is_absent() {
        runner.run(context -> assertThat(context.getBean(LocalMercadoPagoPaymentStore.class)
            .createPreference(REQUEST).checkoutUrl())
            .isEqualTo("http://local.mercadopago/checkout/local-preference-payment-1"));
    }
}
