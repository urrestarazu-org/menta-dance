package com.menta.virtual.domain.model;

import java.util.Objects;

/**
 * A course's category (e.g. "tango", "salsa").
 *
 * <p>Modelled as a value object, not an enum like {@link CourseLevel}: dance
 * styles are catalog data a content manager can add without a code change —
 * an enum would force a deploy for every new category.</p>
 */
public final class CourseCategory {

    private final String value;

    private CourseCategory(String value) {
        this.value = value;
    }

    public static CourseCategory of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CourseCategory cannot be null or blank");
        }
        return new CourseCategory(value);
    }

    public String getValue() {
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
        CourseCategory that = (CourseCategory) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
