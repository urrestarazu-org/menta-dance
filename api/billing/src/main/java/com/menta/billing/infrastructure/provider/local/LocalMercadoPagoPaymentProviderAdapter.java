package com.menta.billing.infrastructure.provider.local;

import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.PaymentProviderPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local-only implementation of the idempotent provider payment lookup port. */
@Component
@Profile("e2e-mercadopago")
public final class LocalMercadoPagoPaymentProviderAdapter implements PaymentProviderPort {

    private final LocalMercadoPagoPaymentStore store;

    public LocalMercadoPagoPaymentProviderAdapter(LocalMercadoPagoPaymentStore store) {
        this.store = store;
    }

    @Override
    public ProviderPaymentResult fetchPayment(String providerPaymentId) {
        return store.fetchPayment(providerPaymentId);
    }
}
