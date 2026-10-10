package com.menta.bff.application.dto;

/**
 * The payment started for a quote.
 *
 * @param paymentId   payment identifier (UUID text), used later to ask for its status
 * @param checkoutUrl provider page where the buyer pays
 */
public record PhysicalCheckout(String paymentId, String checkoutUrl) {
}
