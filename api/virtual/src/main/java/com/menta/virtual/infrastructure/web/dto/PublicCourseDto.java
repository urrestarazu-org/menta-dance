package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicCourseRef;

/** Wire form of the compact course reference carried inside a lesson detail. */
public record PublicCourseDto(
    String courseId,
    String title
) {

    /** Maps the application reference to its wire form. */
    public static PublicCourseDto from(PublicCourseRef ref) {
        return new PublicCourseDto(ref.courseId(), ref.title());
    }
}
