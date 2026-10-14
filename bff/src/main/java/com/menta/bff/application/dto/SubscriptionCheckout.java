package com.menta.bff.application.dto;

/**
 * The payment started for a subscription.
 *
 * @param paymentId   payment identifier (UUID text), used later to ask for its status
 * @param checkoutUrl provider page where the buyer pays
 */
public record SubscriptionCheckout(String paymentId, String checkoutUrl) {
}
