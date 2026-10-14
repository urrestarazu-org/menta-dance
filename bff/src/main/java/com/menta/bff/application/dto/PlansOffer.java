package com.menta.bff.application.dto;

import java.util.Map;

/**
 * What the plans page offers the current viewer on top of the shared catalog.
 *
 * @param action               what each plan's subscribe control does
 * @param notice               the subscription notice to show above the plans
 * @param endDateLabel         end date as {@code dd/MM/yyyy}, for the active and expired notices
 * @param subscriptionId       full subscription id, for the pending notice
 * @param idempotencyKeyByPlanId one render-time idempotency key per plan, only for {@link SubscribeAction#SUBSCRIBE}
 */
public record PlansOffer(
        SubscribeAction action,
        OfferNotice notice,
        String endDateLabel,
        String subscriptionId,
        Map<String, String> idempotencyKeyByPlanId) {

    /** What the "Suscribirme" control of each plan does. */
    public enum SubscribeAction {
        /** No control is rendered. */
        NONE,
        /** A link that sends the visitor to login and back to the plans. */
        LOGIN,
        /** A form that starts the checkout. */
        SUBSCRIBE
    }

    /** The subscription notice shown to a student. */
    public enum OfferNotice {
        NONE,
        ACTIVE,
        PENDING,
        EXPIRED
    }
}
