package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a webhook's manifest timestamp is more than five minutes old
 * (US-BILLING-002 replay protection). The HTTP layer maps this to 401,
 * identical treatment to an invalid signature — a stale, replayable request
 * is never persisted.
 */
public class WebhookTimestampExpiredException extends BusinessException {

    private static final String ERROR_CODE = "WEBHOOK_TIMESTAMP_EXPIRED";

    public WebhookTimestampExpiredException() {
        super(ERROR_CODE, "Webhook timestamp is more than 5 minutes old");
    }
}
