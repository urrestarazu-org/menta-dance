package com.menta.billing.application.dto;

import java.util.UUID;

/**
 * Operator alarm for a settled virtual payment that ended without subscription access (#236).
 * Carries opaque identifiers only: no email, name or amount.
 *
 * @param reason which of the two silent-failure cases occurred.
 * @param paymentId the settled payment (grouping key in the log, never a metric tag).
 * @param subscriptionId the subscription parked as {@code EXCEPTION}, or {@code null} for {@link
 *     SubscriptionFulfillmentAlarmReason#SUBSCRIPTION_MISSING} (there is no row).
 * @param planId the plan identifier the payment targeted ({@code PaymentTarget.Virtual#planId}).
 * @param userId the buyer.
 */
public record SubscriptionFulfillmentAlarm(
    SubscriptionFulfillmentAlarmReason reason,
    UUID paymentId,
    UUID subscriptionId,
    String planId,
    UUID userId
) {
}
