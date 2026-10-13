package com.menta.bff.application.usecase;

import java.util.List;

/**
 * View model for the public page of one physical class.
 *
 * @param courseId      course identifier, used to post a quote request ({@code null} when unavailable)
 * @param title         course title ({@code null} when unavailable)
 * @param levelLabel    Spanish level label, {@code null} when unknown
 * @param professorName professor name
 * @param scheduleLabel weekday and start time, {@code null} when not provided
 * @param priceState    whether the monthly price is published, missing or could not be read
 * @param priceLabel    monthly price in Spanish ("$ 25.000,00 por mes"), {@code null} unless published
 * @param sessions      upcoming sessions already in the academy's time zone
 * @param unavailable   whether the upstream catalog could not be obtained
 */
public record ClassDetailView(
        String courseId,
        String title,
        String levelLabel,
        String professorName,
        String scheduleLabel,
        PriceState priceState,
        String priceLabel,
        List<SessionRow> sessions,
        boolean unavailable) {

    /** The degraded view shown while the catalog is down. */
    public static ClassDetailView unavailableView() {
        return new ClassDetailView(null, null, null, null, null, PriceState.UNAVAILABLE, null, List.of(), true);
    }

    /** Whether the student may ask for a quote: only when a monthly price is published. */
    public boolean canQuote() {
        return priceState == PriceState.PUBLISHED;
    }

    /** Whether there is any upcoming session to list. */
    public boolean hasSessions() {
        return !sessions.isEmpty();
    }

    /** State of the course's monthly price. */
    public enum PriceState {
        /** A monthly price is published. */
        PUBLISHED,
        /** Billing answered that no price is published. */
        NOT_PUBLISHED,
        /** The price could not be read (billing down or unreadable). */
        UNAVAILABLE
    }

    /**
     * One upcoming session.
     *
     * @param sessionId      session identifier, sent back to quote this single class
     * @param whenLabel      readable Spanish date and time in the academy's zone
     * @param availableSpots free spots, 0 when full
     * @param owned          whether the signed-in student already holds a confirmed spot in it
     */
    public record SessionRow(String sessionId, String whenLabel, int availableSpots, boolean owned) {

        /** Whether the session has no free spot left. */
        public boolean full() {
            return availableSpots <= 0;
        }

        /** Free spots in Spanish, or {@code "Completo"}. */
        public String spotsLabel() {
            if (full()) {
                return "Completo";
            }
            return availableSpots + (availableSpots == 1 ? " lugar libre" : " lugares libres");
        }
    }
}
