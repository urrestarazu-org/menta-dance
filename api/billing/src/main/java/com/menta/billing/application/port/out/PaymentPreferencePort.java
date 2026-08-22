package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;

/**
 * Opens a hosted checkout with the payment provider (US-BILLING-010).
 *
 * <p>Separate from {@link PaymentProviderPort} on purpose: that one is an
 * idempotent read the inbox worker may retry freely, this one <em>creates</em>
 * an external charge and must never be retried automatically on an uncertain
 * result (US-BILLING-010 integrity NFR, US-BILLING-002).</p>
 */
public interface PaymentPreferencePort {

    /** @throws RuntimeException on any provider/transport failure — the caller aborts the checkout, never retries. */
    PaymentPreferenceResult createPreference(PaymentPreferenceRequest request);
}
