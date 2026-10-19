package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * The acting user has no subscription at all — no row with status {@code PENDING},
 * {@code ACTIVE}, or {@code EXPIRED} (US-BILLING-004).
 *
 * <p>Deliberately a distinct type from {@link SubscriptionNotFoundException}: that exception is
 * intentionally ambiguous across three cases to prevent enumeration during cancellation
 * (US-BILLING-011). Here the caller is reading their own dashboard — ambiguity buys nothing and
 * only costs clarity (design.md A5, D2).
 */
public class NoSubscriptionException extends BusinessException {

    private static final String ERROR_CODE = "NO_SUBSCRIPTION";

    public NoSubscriptionException() {
        super(ERROR_CODE, "No subscription was found for this user");
    }
}
