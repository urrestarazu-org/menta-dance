package com.menta.physical.application.dto;

import java.util.UUID;

/**
 * Who is reading a student's attendance history and under what authority (#39,
 * US-PHYSICAL-002, design C2). Carries its own {@code studentId} so subject and authority
 * travel together and cannot be mismatched — an illegal "elevated access with no subject" or
 * "instructor scoping with no professor id" state is unrepresentable.
 *
 * <p>{@link Self} and {@link Admin} resolve to the same unrestricted query
 * ({@code GetPhysicalAttendanceHistoryUseCaseImpl}'s {@code switch}) deliberately: they are the
 * same query with different authority, and collapsing them into one variant would lose the
 * distinction {@code SecurityConfig}'s matchers rely on.</p>
 */
public sealed interface AttendanceViewer {

    UUID studentId();

    record Self(UUID studentId) implements AttendanceViewer { }

    record Admin(UUID studentId) implements AttendanceViewer { }

    record InstructorOwnCourses(UUID studentId, UUID professorId) implements AttendanceViewer { }

    /** The self endpoint has no studentId parameter at all — subject == caller, structurally. */
    static AttendanceViewer self(UUID actingUserId) {
        return new Self(actingUserId);
    }

    /** Mirrors this repo's established (actingUserId, actingAsAdmin) controller pair. */
    static AttendanceViewer elevated(UUID studentId, UUID actingUserId, boolean actingAsAdmin) {
        return actingAsAdmin ? new Admin(studentId) : new InstructorOwnCourses(studentId, actingUserId);
    }
}
