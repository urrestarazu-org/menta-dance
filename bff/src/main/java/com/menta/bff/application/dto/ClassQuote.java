package com.menta.bff.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A billing quote for an in-person class purchase, trimmed to what the
 * confirmation page needs.
 *
 * @param quoteId           quote identifier, the only thing checkout needs
 * @param courseId          quoted course
 * @param purchaseType      month or single session
 * @param selectedSessionId quoted session for {@code INDIVIDUAL}, {@code null} for {@code MONTHLY}
 * @param amount            exact amount to pay
 * @param currency          currency code
 * @param available         whether billing saw a free spot when quoting
 * @param expiresAt         when the quote stops being accepted by checkout
 */
public record ClassQuote(
        String quoteId,
        String courseId,
        ClassPurchaseType purchaseType,
        String selectedSessionId,
        BigDecimal amount,
        String currency,
        boolean available,
        Instant expiresAt) {
}
