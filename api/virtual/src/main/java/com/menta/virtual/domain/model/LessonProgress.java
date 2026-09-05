package com.menta.virtual.domain.model;

import com.menta.virtual.domain.exception.InvalidLessonPositionException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A student's saved playback position and completion state for one lesson
 * (US-VIRTUAL-005). One row per {@code (userId, lessonId)} pair — see
 * {@code V19__virtual_lesson_progress.sql}. {@code positionUpdatedAt} is
 * {@code null} until the first {@link #withPosition} call: a row created
 * only by {@link #markCompleted} on a never-played lesson never "touches"
 * a position, so it must not fabricate a timestamp for resume ordering.
 */
public final class LessonProgress {

    private final LessonProgressId id;
    private final UUID userId;
    private final LessonId lessonId;
    private final CourseId courseId;
    private final int positionSeconds;
    private final boolean completed;
    private final Instant completedAt;
    private final Instant positionUpdatedAt;

    public LessonProgress(
        LessonProgressId id, UUID userId, LessonId lessonId, CourseId courseId, int positionSeconds,
        boolean completed, Instant completedAt, Instant positionUpdatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.lessonId = Objects.requireNonNull(lessonId, "lessonId cannot be null");
        this.courseId = Objects.requireNonNull(courseId, "courseId cannot be null");
        this.positionSeconds = positionSeconds;
        this.completed = completed;
        this.completedAt = completedAt;
        this.positionUpdatedAt = positionUpdatedAt;
    }

    public static LessonProgress start(UUID userId, LessonId lessonId, CourseId courseId) {
        return new LessonProgress(LessonProgressId.generate(), userId, lessonId, courseId, 0, false, null, null);
    }

    /** Validates {@code 0 <= newPositionSeconds <= maxSeconds} before saving; never touches completion. */
    public LessonProgress withPosition(int newPositionSeconds, int maxSeconds, Instant now) {
        if (newPositionSeconds < 0 || newPositionSeconds > maxSeconds) {
            throw new InvalidLessonPositionException(newPositionSeconds, maxSeconds);
        }
        return new LessonProgress(
            id, userId, lessonId, courseId, newPositionSeconds, completed, completedAt, now
        );
    }

    /** Sets {@code completed}/{@code completedAt} only; a no-op when already completed (decision 7). */
    public LessonProgress markCompleted(Instant now) {
        if (completed) {
            return this;
        }
        return new LessonProgress(id, userId, lessonId, courseId, positionSeconds, true, now, positionUpdatedAt);
    }

    public LessonProgressId getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LessonId getLessonId() {
        return lessonId;
    }

    public CourseId getCourseId() {
        return courseId;
    }

    public int getPositionSeconds() {
        return positionSeconds;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getPositionUpdatedAt() {
        return positionUpdatedAt;
    }
}
