package com.menta.billing.domain.model;

/**
 * Informative projection of capacity carried by a {@link PhysicalCourseQuote}
 * (US-BILLING-006 escenario 4). Never a reservation — the checkout flow
 * (out of scope for this issue) is what actually holds or rejects capacity.
 */
public enum QuoteAvailability {
    AVAILABLE,
    UNAVAILABLE
}
