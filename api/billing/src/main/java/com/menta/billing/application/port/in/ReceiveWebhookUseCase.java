package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.WebhookSignal;

/**
 * The webhook controller's only use case (US-BILLING-002): verify, dedupe,
 * persist as {@code RECEIVED}. Never queries the provider, never applies any
 * payment effect — that is {@code WebhookVerificationWorker}'s job, off the
 * request thread.
 */
public interface ReceiveWebhookUseCase {

    void receive(WebhookSignal signal);
}
