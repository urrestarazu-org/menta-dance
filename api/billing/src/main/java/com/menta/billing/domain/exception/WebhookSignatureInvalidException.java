package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a Mercado Pago webhook's {@code x-signature} does not match
 * the HMAC-SHA256 computed over the manifest (US-BILLING-002). The HTTP
 * layer maps this to 401 — an unverified webhook is never persisted, never
 * trusted, and never allowed to reach the inbox.
 */
public class WebhookSignatureInvalidException extends BusinessException {

    private static final String ERROR_CODE = "WEBHOOK_SIGNATURE_INVALID";

    public WebhookSignatureInvalidException() {
        super(ERROR_CODE, "Webhook signature verification failed");
    }
}
