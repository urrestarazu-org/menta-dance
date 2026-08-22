package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicCourseRef;

public record PublicCourseDto(
    String courseId,
    String title
) {

    public static PublicCourseDto from(PublicCourseRef ref) {
        return new PublicCourseDto(ref.courseId(), ref.title());
    }
}
