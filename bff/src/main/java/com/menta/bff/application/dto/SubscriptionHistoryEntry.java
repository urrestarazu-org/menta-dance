package com.menta.bff.application.dto;

import java.time.Instant;

/**
 * One subscription of the student's history as {@code GET /api/v1/billing/subscriptions/me/history}
 * reports it (#363), trimmed to what the history page shows.
 *
 * @param planId    plan the subscription belongs to; the page resolves its name from the public catalog
 * @param status    where the subscription stands
 * @param startDate instant on which it started, {@code null} while it is still pending payment
 * @param endDate   instant on which it ends or ended, {@code null} while it is still pending payment
 * @param type      whether it was paid for or granted as a trial
 */
public record SubscriptionHistoryEntry(
        String planId, Status status, Instant startDate, Instant endDate, Type type) {

    /** Subscription states. {@code UNKNOWN} stands for a value this BFF does not know yet. */
    public enum Status {
        PENDING,
        ACTIVE,
        EXPIRED,
        CANCELLED,
        UNKNOWN
    }

    /** Subscription origin. {@code UNKNOWN} stands for a value this BFF does not know yet. */
    public enum Type {
        PAID,
        TRIAL,
        UNKNOWN
    }
}
