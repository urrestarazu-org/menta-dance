package com.menta.virtual.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object identifying a {@link VirtualCourse}. Shares the UUID space with Physical's course IDs. */
public final class CourseId {

    private final UUID value;

    private CourseId(UUID value) {
        this.value = value;
    }

    public static CourseId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("CourseId cannot be null");
        }
        return new CourseId(value);
    }

    public static CourseId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CourseId cannot be null or empty");
        }
        try {
            return new CourseId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid CourseId format: " + value, e);
        }
    }

    public static CourseId generate() {
        return new CourseId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CourseId courseId = (CourseId) o;
        return Objects.equals(value, courseId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
