package com.menta.bff.application.usecase;

import java.time.Instant;
import java.util.List;

/**
 * View model of the reception screen's agenda: the sessions that start today.
 *
 * @param dayLabel    Spanish label of the day shown, e.g. {@code "viernes 13 de noviembre"}
 * @param rows        sessions of the day, soonest first (empty when unavailable)
 * @param unavailable whether the agenda could not be obtained
 */
public record ReceptionSessionsView(String dayLabel, List<Row> rows, boolean unavailable) {

    /**
     * One session of the agenda.
     *
     * @param sessionId   session identifier, for the link to its roster
     * @param courseName  name of the class
     * @param startsAt    start of the session, for the machine-readable time
     * @param timeLabel   {@code HH:mm} in the academy's zone
     * @param cancelled   whether the session is cancelled
     * @param capacity    total spots
     * @param occupied    spots that count against the capacity
     */
    public record Row(
            String sessionId, String courseName, Instant startsAt, String timeLabel, boolean cancelled,
            int capacity, int occupied) {
    }

    /** The degraded view shown while the Physical API is down: the day stays, there are no rows. */
    public static ReceptionSessionsView unavailableView(String dayLabel) {
        return new ReceptionSessionsView(dayLabel, List.of(), true);
    }

    /** Whether the Physical API answered but no session starts today. */
    public boolean empty() {
        return !unavailable && rows.isEmpty();
    }
}
