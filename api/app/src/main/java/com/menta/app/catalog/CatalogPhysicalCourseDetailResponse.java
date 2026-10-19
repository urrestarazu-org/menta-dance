package com.menta.app.catalog;

import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import java.util.List;

/**
 * Public detail of a {@code PHYSICAL} course (#107): course data plus the
 * upcoming scheduled sessions with their live availability. Carries no prices
 * and no internal capacity counts ({@code assignedSpots},
 * {@code activeCapacityHolds}) — only {@code availableSpots} is published.
 */
public record CatalogPhysicalCourseDetailResponse(
    String courseId,
    CourseModality modality,
    String title,
    String level,
    PhysicalDetailBlock physical
) implements CatalogCourseDetail {

    /** Explicit mapping: {@code PhysicalSessionAvailability}'s internal counts are dropped. */
    public static CatalogPhysicalCourseDetailResponse from(
        PhysicalCourseSummary course, List<PhysicalSessionAvailability> sessions
    ) {
        return new CatalogPhysicalCourseDetailResponse(
            course.courseId(),
            CourseModality.PHYSICAL,
            course.title(),
            course.level(),
            new PhysicalDetailBlock(
                course.professorName(),
                course.dayOfWeek(),
                course.startTime(),
                course.capacity(),
                sessions.stream().map(PublicSession::from).toList()
            )
        );
    }

    /** Physical block of the detail: the course schedule plus its upcoming sessions. */
    public record PhysicalDetailBlock(
        String professorName,
        String dayOfWeek,
        String startTime,
        int capacity,
        List<PublicSession> sessions
    ) {
    }

    /** {@code scheduledAt} is the port's ISO-8601 UTC instant, passed through. */
    public record PublicSession(
        String sessionId, String scheduledAt, int capacity, int availableSpots
    ) {

        static PublicSession from(PhysicalSessionAvailability session) {
            return new PublicSession(
                session.sessionId(),
                session.scheduledAt(),
                session.capacity(),
                session.availableSpots()
            );
        }
    }
}
