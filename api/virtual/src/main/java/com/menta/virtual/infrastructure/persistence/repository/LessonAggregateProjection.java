package com.menta.virtual.infrastructure.persistence.repository;

import java.util.UUID;

/** Aggregate projection: number of lessons and total duration per course. */
public interface LessonAggregateProjection {

    UUID getCourseId();

    Long getLessonCount();

    Long getTotalDurationMinutes();
}
