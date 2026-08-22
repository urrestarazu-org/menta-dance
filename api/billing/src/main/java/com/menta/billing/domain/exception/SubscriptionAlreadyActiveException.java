package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;
import java.time.Instant;
import java.util.Optional;

/**
 * The user already holds a subscription that occupies their single slot
 * (US-BILLING-010 escenario 3). Nothing is written and no provider charge is
 * started.
 *
 * <p>{@code currentEndDate} is absent when the blocking subscription is still
 * {@code PENDING} — a checkout the user started but has not paid yet has no
 * vigencia to report.</p>
 */
public class SubscriptionAlreadyActiveException extends BusinessException {

    private static final String ERROR_CODE = "SUBSCRIPTION_ALREADY_ACTIVE";

    private final Instant currentEndDate;

    public SubscriptionAlreadyActiveException(Instant currentEndDate) {
        super(ERROR_CODE, "User already has a subscription in force");
        this.currentEndDate = currentEndDate;
    }

    public Optional<Instant> getCurrentEndDate() {
        return Optional.ofNullable(currentEndDate);
    }
}
