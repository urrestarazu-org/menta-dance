package com.menta.virtual.infrastructure.persistence.repository;

import java.util.UUID;

/** Aggregate projection: number of lessons and total duration per course. */
public interface LessonAggregateProjection {

    /** Returns the id of the course the aggregate belongs to. */
    UUID getCourseId();

    /** Returns the number of lessons of the course. */
    Long getLessonCount();

    /** Returns the sum of the lesson durations in minutes; the query coalesces an empty sum to {@code 0}. */
    Long getTotalDurationMinutes();
}
