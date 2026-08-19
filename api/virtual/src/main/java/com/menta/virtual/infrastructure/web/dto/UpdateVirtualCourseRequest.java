package com.menta.virtual.infrastructure.web.dto;

/**
 * Partial update — every field is nullable and {@code null} means "not
 * present in this PATCH, leave unchanged". No {@code @NotBlank} here on
 * purpose (it is not null-safe by Jakarta Bean Validation convention and
 * would reject a legitimately absent field, breaking every partial update
 * that omits it — same lesson learned in {@code physical}'s
 * {@code UpdatePhysicalCourseRequest}, #109).
 */
public record UpdateVirtualCourseRequest(
    String title,
    String shortDescription,
    String description,
    String imageUrl,
    String category,
    String level,
    Boolean premium
) {
}
