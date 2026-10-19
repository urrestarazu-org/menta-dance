package com.menta.billing.application.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Why a settled virtual payment ended without subscription access (#236). The {@link #code()}
 * value is part of the locked alarm log contract consumed by the Grafana LogQL rule, so it must
 * stay stable.
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum SubscriptionFulfillmentAlarmReason {

    /** The pending subscription's plan could not be loaded; it was parked as {@code EXCEPTION}. */
    PLAN_MISSING("plan_missing"),

    /** A Completed payment has no subscription row at all. */
    SUBSCRIPTION_MISSING("subscription_missing");

    private final String code;
}
