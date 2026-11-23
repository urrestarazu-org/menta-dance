package com.menta.virtual.infrastructure.persistence.repository;

/** JPQL fragments shared by the lesson queries; the lesson entity is always aliased {@code l}. */
final class LessonJpql {

    /**
     * Mirrors {@code VirtualLesson#isComplete()}: the lesson has a video (not null and not blank). Students
     * only see, count and progress through such lessons (#851).
     */
    static final String HAS_VIDEO = "l.videoId IS NOT NULL AND TRIM(l.videoId) <> ''";

    private LessonJpql() {
    }
}
