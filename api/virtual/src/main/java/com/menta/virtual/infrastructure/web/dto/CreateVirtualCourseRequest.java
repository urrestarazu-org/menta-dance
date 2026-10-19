package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code professorId} is {@code null} for an INSTRUCTOR creating their own
 * course — it is resolved to the caller's own id; a non-null value that
 * doesn't match is rejected (see {@code ProfessorMismatchException}). Only
 * an ADMIN may assign an arbitrary {@code professorId}.
 */
public record CreateVirtualCourseRequest(
    @NotBlank String title,
    @NotBlank String shortDescription,
    @NotBlank String description,
    String professorId,
    @NotBlank String imageUrl,
    @NotBlank String category,
    @NotBlank String level
) {
}
