package com.menta.bff.application.dto;

import com.menta.bff.domain.model.CourseModality;

/**
 * One entry of the public course catalog, trimmed from upstream's {@code
 * CatalogCourseResponse} to what the home page renders.
 *
 * @param courseId      course identifier
 * @param modality      delivery modality
 * @param title         course title
 * @param level         upstream level code (e.g. {@code BEGINNER}), may be {@code null}
 * @param professorName professor name; present only for physical courses
 * @param dayOfWeek     upstream weekday code (e.g. {@code MONDAY}), in UTC as upstream sends it (ADR-0048);
 *                      physical courses only
 * @param startTime     start time as {@code HH:mm}, in UTC as upstream sends it (ADR-0048); physical courses only
 */
public record CatalogCourse(
        String courseId,
        CourseModality modality,
        String title,
        String level,
        String professorName,
        String dayOfWeek,
        String startTime) {
}
