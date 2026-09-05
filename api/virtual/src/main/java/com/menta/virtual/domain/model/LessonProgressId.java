package com.menta.virtual.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object identifying a {@link LessonProgress} row (surrogate id, never exposed on the wire). */
public final class LessonProgressId {

    private final UUID value;

    private LessonProgressId(UUID value) {
        this.value = value;
    }

    public static LessonProgressId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("LessonProgressId cannot be null");
        }
        return new LessonProgressId(value);
    }

    public static LessonProgressId generate() {
        return new LessonProgressId(UUID.randomUUID());
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
        LessonProgressId that = (LessonProgressId) o;
        return Objects.equals(value, that.value);
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
