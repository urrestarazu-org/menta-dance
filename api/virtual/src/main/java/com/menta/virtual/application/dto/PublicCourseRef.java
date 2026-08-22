package com.menta.virtual.application.dto;

import com.menta.virtual.domain.model.CourseId;

/** Compact course reference carried inside a public lesson detail. */
public record PublicCourseRef(
    String courseId,
    String title
) {

    public static PublicCourseRef of(CourseId id, String title) {
        return new PublicCourseRef(id.toString(), title);
    }
}
