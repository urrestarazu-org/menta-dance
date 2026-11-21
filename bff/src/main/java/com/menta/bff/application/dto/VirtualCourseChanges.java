package com.menta.bff.application.dto;

/**
 * The fields of a virtual course to change: a {@code null} field is not sent and the API leaves it as it is. The
 * owner of a course cannot be changed.
 */
public record VirtualCourseChanges(
        String title, String shortDescription, String description, String imageUrl, String category,
        VirtualCourseLevel level, Boolean premium) {
}
