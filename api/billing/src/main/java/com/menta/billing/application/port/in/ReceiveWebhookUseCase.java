package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.WebhookSignal;

/**
 * The webhook controller's only use case (US-BILLING-002): verify, dedupe,
 * persist as {@code RECEIVED}. Never queries the provider, never applies any
 * payment effect — that is {@code WebhookVerificationWorker}'s job, off the
 * request thread.
 */
public interface ReceiveWebhookUseCase {

    /**
     * Verifies a webhook notification and records it for later processing.
     *
     * <p>Fails with {@code WebhookSignatureInvalidException} when the signature is malformed or
     * does not match, and with {@code WebhookTimestampExpiredException} when the signed timestamp
     * is more than five minutes old. A valid notification is appended to the inbox keyed by data id
     * and request id, so a redelivery is not stored twice.
     */
    void receive(WebhookSignal signal);
}
