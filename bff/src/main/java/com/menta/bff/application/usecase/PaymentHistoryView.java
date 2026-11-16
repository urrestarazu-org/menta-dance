package com.menta.bff.application.usecase;

import java.util.List;

/**
 * View model of the payments section of the history page (#363), already in Spanish.
 *
 * @param rows        payments, newest first (empty when unavailable)
 * @param unavailable whether the payments could not be read right now
 */
public record PaymentHistoryView(List<Row> rows, boolean unavailable) {

    /** Defensive copy, so the view cannot change after it is built. */
    public PaymentHistoryView {
        rows = List.copyOf(rows);
    }

    /** Whether the API answered but the student has no payment. */
    public boolean empty() {
        return !unavailable && rows.isEmpty();
    }

    /**
     * One payment.
     *
     * @param dateLabel   creation date as {@code dd/MM/yyyy} in the academy's zone
     * @param concept     what was bought
     * @param amountLabel amount with its currency, formatted for es-AR
     * @param methodLabel payment method in Spanish
     * @param statusLabel state in Spanish
     * @param tone        badge weight of the state
     */
    public record Row(
            String dateLabel, String concept, String amountLabel, String methodLabel, String statusLabel,
            BadgeTone tone) {
    }
}
