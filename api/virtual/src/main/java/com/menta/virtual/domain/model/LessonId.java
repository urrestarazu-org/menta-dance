package com.menta.virtual.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object identifying a {@link VirtualLesson}. */
public final class LessonId {

    private final UUID value;

    private LessonId(UUID value) {
        this.value = value;
    }

    public static LessonId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("LessonId cannot be null");
        }
        return new LessonId(value);
    }

    public static LessonId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LessonId cannot be null or empty");
        }
        try {
            return new LessonId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid LessonId format: " + value, e);
        }
    }

    public static LessonId generate() {
        return new LessonId(UUID.randomUUID());
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
        LessonId lessonId = (LessonId) o;
        return Objects.equals(value, lessonId.value);
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
