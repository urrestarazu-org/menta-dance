package com.menta.virtual.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object identifying a {@link VirtualLesson}. */
public final class LessonId {

    private final UUID value;

    private LessonId(UUID value) {
        this.value = value;
    }

    /** Wraps an existing UUID; {@code null} is rejected with {@link IllegalArgumentException}. */
    public static LessonId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("LessonId cannot be null");
        }
        return new LessonId(value);
    }

    /**
     * Parses the textual form of a UUID; a {@code null}, blank or malformed value is rejected with
     * {@link IllegalArgumentException}.
     */
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

    /** Creates an id backed by a random UUID. */
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
