package com.menta.billing.infrastructure.provider.local;

import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.dto.ProviderPaymentResult;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    private final String checkoutBackUrl;
    private final Map<String, PaymentPreferenceResult> preferences = new ConcurrentHashMap<>();
    private final Map<String, PaymentPreferenceRequest> preferenceRequests = new ConcurrentHashMap<>();
    private final Map<String, ProviderPaymentResult> payments = new ConcurrentHashMap<>();

    public LocalMercadoPagoPaymentStore() {
        this("");
    }

    /**
     * Creates the store with the buyer's return URL, when there is one.
     *
     * @param checkoutBackUrl the buyer's return URL ({@code billing.mercadopago.checkout-back-url}). When
     *     set, the local checkout URL is that URL, so a browser "pays" and lands straight back on the web
     *     client; when blank, the unresolvable {@value #LOCAL_CHECKOUT_BASE_URL} URL is kept.
     */
    @Autowired
    public LocalMercadoPagoPaymentStore(@Value("${billing.mercadopago.checkout-back-url:}") String checkoutBackUrl) {
        this.checkoutBackUrl = checkoutBackUrl == null ? "" : checkoutBackUrl.trim();
    }

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
            "local-preference-" + reference, checkoutUrl("local-preference-" + reference)
        ));
    }

    private String checkoutUrl(String preferenceId) {
        return checkoutBackUrl.isEmpty() ? LOCAL_CHECKOUT_BASE_URL + preferenceId : checkoutBackUrl;
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
