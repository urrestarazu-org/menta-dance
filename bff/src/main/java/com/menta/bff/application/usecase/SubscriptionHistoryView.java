package com.menta.bff.application.usecase;

import java.util.List;

/**
 * View model of the subscriptions section of the history page (#363), already in Spanish.
 *
 * @param rows        subscriptions, newest first (empty when unavailable)
 * @param unavailable whether the subscriptions could not be read right now
 */
public record SubscriptionHistoryView(List<Row> rows, boolean unavailable) {

    /** Defensive copy, so the view cannot change after it is built. */
    public SubscriptionHistoryView {
        rows = List.copyOf(rows);
    }

    /** Whether the API answered but the student has no subscription. */
    public boolean empty() {
        return !unavailable && rows.isEmpty();
    }

    /**
     * One subscription.
     *
     * @param planName    plan name, or the generic label when the catalog could not name it
     * @param statusLabel state in Spanish
     * @param tone        badge weight of the state
     * @param trial       whether it is a trial granted by an admin
     * @param startLabel  start date as {@code dd/MM/yyyy}, or "Sin fecha"
     * @param endLabel    end date as {@code dd/MM/yyyy}, or "Sin fecha"
     */
    public record Row(
            String planName, String statusLabel, BadgeTone tone, boolean trial, String startLabel,
            String endLabel) {
    }
}
