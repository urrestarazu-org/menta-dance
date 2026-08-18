package com.menta.physical.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A concrete scheduled occurrence of a {@link PhysicalCourse} (US-PHYSICAL-003).
 *
 * <p>{@code assignedSpots}/{@code activeCapacityHolds} are pre-aggregated by
 * the persistence adapter via live {@code COUNT} queries against
 * {@code physical_capacity_assignments}/{@code physical_capacity_holds} at
 * read time — never a cached counter that could drift under concurrent
 * writes. {@code availableSpots} is therefore always consistent with the
 * rows actually committed at the moment of the read, not a separately
 * maintained value that a concurrent writer could desync (mirrors {@code
 * VirtualCourse}'s pre-aggregated {@code moduleCount}/{@code lessonCount}).
 * This issue never creates assignments or holds itself — that write path is
 * explicitly out of scope.</p>
 */
public final class PhysicalSession {

    private final SessionId id;
    private final CourseId courseId;
    private final Instant scheduledAt;
    private final int capacity;
    private final int assignedSpots;
    private final int activeCapacityHolds;

    public PhysicalSession(
        SessionId id, CourseId courseId, Instant scheduledAt,
        int capacity, int assignedSpots, int activeCapacityHolds
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.courseId = Objects.requireNonNull(courseId, "courseId cannot be null");
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt cannot be null");
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity cannot be negative");
        }
        if (assignedSpots < 0) {
            throw new IllegalArgumentException("assignedSpots cannot be negative");
        }
        if (activeCapacityHolds < 0) {
            throw new IllegalArgumentException("activeCapacityHolds cannot be negative");
        }
        this.capacity = capacity;
        this.assignedSpots = assignedSpots;
        this.activeCapacityHolds = activeCapacityHolds;
    }

    /**
     * Floored at zero: a bug in a future write path that oversells a
     * session must never surface here as a negative, nonsensical count.
     */
    public int getAvailableSpots() {
        return Math.max(0, capacity - assignedSpots - activeCapacityHolds);
    }

    public SessionId getId() {
        return id;
    }

    public CourseId getCourseId() {
        return courseId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getAssignedSpots() {
        return assignedSpots;
    }

    public int getActiveCapacityHolds() {
        return activeCapacityHolds;
    }
}
