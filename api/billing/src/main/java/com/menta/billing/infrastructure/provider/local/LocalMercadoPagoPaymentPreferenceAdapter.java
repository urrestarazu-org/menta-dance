package com.menta.billing.infrastructure.provider.local;

import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local-only implementation of the checkout-preference provider port. */
@Component
@Profile("e2e-mercadopago")
public final class LocalMercadoPagoPaymentPreferenceAdapter implements PaymentPreferencePort {

    private final LocalMercadoPagoPaymentStore store;

    public LocalMercadoPagoPaymentPreferenceAdapter(LocalMercadoPagoPaymentStore store) {
        this.store = store;
    }

    @Override
    public PaymentPreferenceResult createPreference(PaymentPreferenceRequest request) {
        return store.createPreference(request);
    }
}
