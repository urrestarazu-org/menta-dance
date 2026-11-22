package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.dto.VirtualCourseLevel;
import com.menta.bff.application.dto.VirtualCourseStatus;

/**
 * A virtual course in the management list, worded for the screen.
 *
 * @param courseId     course identifier
 * @param title        course name
 * @param category     free-text category
 * @param levelLabel   Spanish level, {@code "Sin dato"} when the BFF does not know it
 * @param statusLabel  Spanish status, {@code "Sin dato"} when the BFF does not know it
 * @param premium      whether the course needs a subscription
 * @param canPublish    whether the screen offers to publish: only a draft
 * @param canUnpublish whether the screen offers to take it back to draft: only a published one
 * @param canDelete     whether the screen offers to delete: only a draft
 */
public record ManagedVirtualCourseRow(
        String courseId, String title, String category, String levelLabel, String statusLabel, boolean premium,
        boolean canPublish, boolean canUnpublish, boolean canDelete) {

    private static final String NO_DATA = "Sin dato";

    /** Words a course. The actions are only a hint for the screen: the API decides each one. */
    static ManagedVirtualCourseRow of(ManagedVirtualCourse course) {
        VirtualCourseStatus status = course.status();
        return new ManagedVirtualCourseRow(course.courseId(), course.title(), course.category(),
                levelLabel(course.level()), statusLabel(status), course.premium(),
                status == VirtualCourseStatus.DRAFT, status == VirtualCourseStatus.PUBLISHED,
                status == VirtualCourseStatus.DRAFT);
    }

    private static String levelLabel(VirtualCourseLevel level) {
        return switch (level) {
            case BEGINNER -> "Principiante";
            case INTERMEDIATE -> "Intermedio";
            case ADVANCED -> "Avanzado";
            case UNKNOWN -> NO_DATA;
        };
    }

    private static String statusLabel(VirtualCourseStatus status) {
        return switch (status) {
            case DRAFT -> "Borrador";
            case PUBLISHED -> "Publicado";
            case ARCHIVED -> "Archivado";
            case UNKNOWN -> NO_DATA;
        };
    }
}
