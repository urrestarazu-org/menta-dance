package com.menta.virtual.application.dto;

import java.util.Optional;

/** Partial update — an absent field means "leave unchanged". */
public record UpdateVirtualCourseCommand(
    Optional<String> title,
    Optional<String> shortDescription,
    Optional<String> description,
    Optional<String> imageUrl,
    Optional<String> category,
    Optional<String> level,
    Optional<Boolean> premium
) {
}
