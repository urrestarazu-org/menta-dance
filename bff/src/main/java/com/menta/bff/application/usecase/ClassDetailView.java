package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ClassPricing;
import java.util.List;

/**
 * Rendering data for the class page.
 *
 * <p>{@code pricing} is {@code null} when billing has not published a price
 * for the course yet: the page still shows the course and its sessions but
 * offers no buy action, since there is nothing billing would quote.
 * </p>
 *
 * @param courseId      course identifier
 * @param title         course title
 * @param level         Spanish level label
 * @param professorName teacher's display name
 * @param schedule      weekly schedule label
 * @param pricing       published pricing, or {@code null} when none is published
 * @param sessions      upcoming sessions, ascending
 */
public record ClassDetailView(
        String courseId,
        String title,
        String level,
        String professorName,
        String schedule,
        ClassPricing pricing,
        List<SessionRow> sessions) {

    /** Whether any buy action may be offered at all. */
    public boolean purchasable() {
        return pricing != null && sessions.stream().anyMatch(session -> !session.soldOut());
    }

    /**
     * One session row.
     *
     * @param sessionId      session identifier, sent back for an individual quote
     * @param startsAt       start label in the academy zone
     * @param availableSpots free spots right now
     */
    public record SessionRow(String sessionId, String startsAt, int availableSpots) {

        /** Whether the session has no free spot left. */
        public boolean soldOut() {
            return availableSpots <= 0;
        }
    }
}
