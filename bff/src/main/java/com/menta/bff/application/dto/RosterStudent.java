package com.menta.bff.application.dto;

import java.time.Instant;

/**
 * A student with a confirmed spot in a session, as the roster lists them.
 *
 * @param studentId      student identifier, the one the manual check-in needs
 * @param displayName    name shown to the staff (the upstream already falls back to the email or a placeholder)
 * @param assignmentType how the student got the spot ({@code MONTHLY} or {@code INDIVIDUAL}), may be {@code null}
 * @param present        whether the student already checked in to this session
 * @param checkedInAt    instant of the check-in, {@code null} while not present
 * @param checkInKind    {@code QR} or {@code MANUAL}, {@code null} while not present
 */
public record RosterStudent(
        String studentId, String displayName, String assignmentType, boolean present, Instant checkedInAt,
        String checkInKind) {
}
