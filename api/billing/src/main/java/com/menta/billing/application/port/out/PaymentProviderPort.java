package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.ProviderPaymentResult;

/**
 * Reads a payment's current state from the provider by its own id
 * (ADR-0038). An idempotent read: safe for the inbox worker to retry with
 * backoff on failure — see the real adapter for why retries live in the
 * worker's cycle, not in this port's implementation.
 */
public interface PaymentProviderPort {

    /** @throws RuntimeException on any provider/transport failure — the caller retries via the inbox. */
    ProviderPaymentResult fetchPayment(String providerPaymentId);
}
