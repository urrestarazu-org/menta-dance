package com.menta.bff.domain.membership;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A row of the price table, ready to render.
 *
 * @param label          text of the "Alta hasta" column
 * @param formattedPrice monthly installment formatted for display (for example {@code "$ 25.000"})
 * @param installments   number of installments
 * @param formattedTotal total (price x installments) formatted for display
 * @param state          state of the row according to today's date
 * @param payUrl         subscription link
 * @param exclusive      whether the row is the perfect-attendance benefit
 * @param deadline       last day (inclusive) on which this price applies
 */
public record PriceRow(
        String label,
        String formattedPrice,
        int installments,
        String formattedTotal,
        PriceRowState state,
        String payUrl,
        boolean exclusive,
        LocalDate deadline) {

    /** Argentina does not observe daylight saving time. */
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    /**
     * Instant at which this price stops applying: 00:00 of the day after the deadline, Argentina time.
     *
     * @return the instant in ISO-8601 UTC (for example {@code "2026-11-08T03:00:00Z"})
     */
    public String expiresAt() {
        return deadline.plusDays(1).atStartOfDay(ARGENTINA).toInstant().toString();
    }

    /** Whether this is the price in force today. */
    public boolean current() {
        return state == PriceRowState.CURRENT;
    }

    /** Whether the row shows a pay button: in force today and with a link. */
    public boolean payable() {
        return current() && payUrl != null && !payUrl.isBlank();
    }

    /** Whether this is the price in force but its subscription link has not been loaded yet. */
    public boolean awaitingLink() {
        return current() && !payable();
    }

    /** Whether the row is shown locked (future or expired). */
    public boolean locked() {
        return state != PriceRowState.CURRENT;
    }

    /** Whether the deadline of this row has passed. */
    public boolean expired() {
        return state == PriceRowState.EXPIRED;
    }
}
