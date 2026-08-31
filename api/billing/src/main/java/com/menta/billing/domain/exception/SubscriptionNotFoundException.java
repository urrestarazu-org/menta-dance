package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * No cancellable subscription was found for the request (US-BILLING-011).
 *
 * <p>One exception for three cases: the subscription does not exist, it exists but is not
 * {@code ACTIVE}, or (on the admin route) the caller is not an admin — same anti-enumeration
 * discipline {@link PlanNotAvailableException} already applies. A caller must not be able to
 * tell "does not exist" apart from "exists but I cannot touch it" (design.md A5).
 */
public class SubscriptionNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "SUBSCRIPTION_NOT_FOUND";

    public SubscriptionNotFoundException() {
        super(ERROR_CODE, "No cancellable subscription was found");
    }
}
