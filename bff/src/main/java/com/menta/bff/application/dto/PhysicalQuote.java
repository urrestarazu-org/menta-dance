package com.menta.bff.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A quote for a physical class, trimmed from the upstream
 * {@code PhysicalCourseQuoteResponse} to what the quote page renders.
 *
 * @param id                    quote identifier (UUID text)
 * @param purchaseType          monthly or individual
 * @param scheduledSessionCount sessions in the quoted period
 * @param selectedSessionId     the single session of an individual quote, {@code null} otherwise
 * @param amount                price to pay
 * @param currency              ISO 4217 currency code
 * @param unavailable           whether the capacity projection says there is no room (informative only)
 * @param expiresAt             instant after which the quote can no longer be paid
 */
public record PhysicalQuote(
        String id,
        PhysicalPurchaseType purchaseType,
        int scheduledSessionCount,
        String selectedSessionId,
        BigDecimal amount,
        String currency,
        boolean unavailable,
        Instant expiresAt) {
}
