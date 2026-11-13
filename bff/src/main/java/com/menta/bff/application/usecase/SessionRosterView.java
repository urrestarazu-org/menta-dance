package com.menta.bff.application.usecase;

import java.util.List;

/**
 * View model of the roster of one session on the reception screen.
 *
 * @param outcome       how the lookup ended
 * @param courseName    name of the class (only for {@link Outcome#OK})
 * @param momentLabel   Spanish date and time of the start in the academy's zone (only for {@link Outcome#OK})
 * @param cancelled     whether the session is cancelled
 * @param capacity      total spots of the session
 * @param enrolled      students with a confirmed spot, before the search filter
 * @param present       how many of them already checked in, before the search filter
 * @param text          search text in force, {@code null} for none
 * @param rows          students that match the search, in upstream order
 */
public record SessionRosterView(
        Outcome outcome, String courseName, String momentLabel, boolean cancelled, int capacity, int enrolled,
        int present, String text, List<Row> rows) {

    /** How the lookup ended. */
    public enum Outcome {
        /** The roster was obtained. */
        OK,
        /** The session does not exist, or its identifier is not one. */
        NOT_FOUND,
        /** The API could not be reached or answered something unusable. */
        UNAVAILABLE
    }

    /**
     * One student of the roster.
     *
     * @param studentId       student identifier, for the check-in form
     * @param displayName     name shown to the staff
     * @param assignmentLabel Spanish label of how the student got the spot, {@code null} when unknown
     * @param present         whether the student already checked in
     * @param statusLabel     {@code Presente} or {@code Pendiente}: the status is always text, never only color
     * @param checkedInLabel  {@code HH:mm} of the check-in, {@code null} while pending
     * @param kindLabel       how it was recorded ({@code QR} or {@code manual}), {@code null} while pending
     * @param canRegister     whether the screen offers "Registrar asistencia" for this student
     */
    public record Row(
            String studentId, String displayName, String assignmentLabel, boolean present, String statusLabel,
            String checkedInLabel, String kindLabel, boolean canRegister) {
    }

    /** A view without a roster: the session was not found or the API is down. The search text stays. */
    public static SessionRosterView failed(Outcome outcome, String text) {
        return new SessionRosterView(outcome, null, null, false, 0, 0, 0, text, List.of());
    }

    /** Whether the roster was obtained. */
    public boolean ok() {
        return outcome == Outcome.OK;
    }

    /** Whether the session has no student with a confirmed spot at all. */
    public boolean noStudents() {
        return ok() && enrolled == 0;
    }

    /** Whether there are students but none matches the search. */
    public boolean noMatches() {
        return ok() && enrolled > 0 && rows.isEmpty();
    }

    /** Whether a search narrows the list. */
    public boolean filtered() {
        return text != null;
    }
}
