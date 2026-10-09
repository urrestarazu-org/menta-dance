package com.menta.bff.domain.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** How a course is delivered: online lessons or in-person classes. */
public enum CourseModality {
    VIRTUAL,
    PHYSICAL;

    /**
     * Parses a catalog filter value (e.g. the {@code modality} query parameter).
     *
     * @param value raw filter value, may be {@code null}
     * @return the matching modality, or empty when the value is null, blank or unknown (meaning "all")
     */
    public static Optional<CourseModality> fromFilter(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(m -> m.name().equals(normalized)).findFirst();
    }
}
