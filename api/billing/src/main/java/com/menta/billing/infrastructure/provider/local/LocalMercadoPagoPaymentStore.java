package com.menta.billing.infrastructure.provider.local;

import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.dto.ProviderPaymentResult;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Profile-scoped deterministic state for local Mercado Pago E2E journeys.
 *
 * <p>It is deliberately infrastructure-only. It neither creates Billing
 * aggregates nor mutates webhook inbox rows; the normal checkout and worker
 * retain those responsibilities.</p>
 */
@Component
@Profile("e2e-mercadopago")
public final class LocalMercadoPagoPaymentStore {

    private static final String LOCAL_CHECKOUT_BASE_URL = "http://local.mercadopago/checkout/";

    private final Map<String, PaymentPreferenceResult> preferences = new ConcurrentHashMap<>();
    private final Map<String, PaymentPreferenceRequest> preferenceRequests = new ConcurrentHashMap<>();
    private final Map<String, ProviderPaymentResult> payments = new ConcurrentHashMap<>();

    /**
     * Registers a fake preference for the request and returns it.
     *
     * <p>The store is idempotent per external reference: the first request and its preference are
     * kept, and later calls with the same reference return the same preference id and local
     * checkout URL.
     */
    public PaymentPreferenceResult createPreference(PaymentPreferenceRequest request) {
        preferenceRequests.putIfAbsent(request.externalReference(), request);
        return preferences.computeIfAbsent(request.externalReference(), reference -> new PaymentPreferenceResult(
            "local-preference-" + reference, LOCAL_CHECKOUT_BASE_URL + "local-preference-" + reference
        ));
    }

    PaymentPreferenceRequest preferenceRequest(String externalReference) {
        PaymentPreferenceRequest request = preferenceRequests.get(externalReference);
        if (request == null) {
            throw new IllegalArgumentException("Unknown local Mercado Pago reference: " + externalReference);
        }
        return request;
    }

    /**
     * Returns the payment registered under a provider payment id.
     *
     * <p>Only payments prepared by {@code LocalWebhookPreparationService} are known; an unknown id
     * fails with {@link IllegalArgumentException}.
     */
    public ProviderPaymentResult fetchPayment(String providerPaymentId) {
        ProviderPaymentResult payment = payments.get(providerPaymentId);
        if (payment == null) {
            throw new IllegalArgumentException("Unknown local Mercado Pago payment: " + providerPaymentId);
        }
        return payment;
    }

    void registerPayment(String providerPaymentId, ProviderPaymentResult payment) {
        payments.put(providerPaymentId, payment);
    }
}
