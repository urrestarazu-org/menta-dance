package com.menta.virtual.application.dto;

import java.util.UUID;

/**
 * Input of the course-creation use case; {@code category} and {@code level} arrive as raw strings
 * that the use case parses.
 *
 * @param professorId {@code null} means "default to the authenticated
 *     instructor" — only meaningful when the actor is ADMIN; an INSTRUCTOR
 *     supplying a non-null, non-self id is rejected (see {@code
 *     ProfessorMismatchException}).
 */
public record CreateVirtualCourseCommand(
    String title,
    String shortDescription,
    String description,
    UUID professorId,
    String imageUrl,
    String category,
    String level
) {
}
