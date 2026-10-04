package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ClassQuote;

/**
 * Result of {@link QuoteClassUseCase}: the confirmation page's rendering
 * decision. Only {@link Payable} carries an idempotency key, so a sold-out
 * or rejected quote cannot render a payment form even by accident.
 */
public sealed interface ClassQuoteView {

    /** Course the quote was requested for, for the page's back link. */
    String courseId();

    /**
     * A quote with a free spot: render the amount and the payment form.
     *
     * @param courseId       quoted course
     * @param quote          the quote
     * @param expiresAt      expiry label in the academy zone
     * @param idempotencyKey key minted for this page, sent back with the payment form
     */
    record Payable(String courseId, ClassQuote quote, String expiresAt, String idempotencyKey)
            implements ClassQuoteView {
    }

    /**
     * Billing quoted the class but saw no free spot.
     *
     * @param courseId quoted course
     */
    record SoldOut(String courseId) implements ClassQuoteView {
    }

    /**
     * Billing refused to quote (no price, unknown session, no sessions this month).
     *
     * @param courseId course the quote was requested for
     */
    record Rejected(String courseId) implements ClassQuoteView {
    }
}
