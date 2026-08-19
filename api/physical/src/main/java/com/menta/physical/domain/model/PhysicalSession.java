package com.menta.physical.domain.model;

import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import java.time.Instant;
import java.util.Objects;

/**
 * A concrete scheduled occurrence of a {@link PhysicalCourse} (US-PHYSICAL-003, US-PHYSICAL-006).
 *
 * <p>{@code assignedSpots}/{@code activeCapacityHolds} are pre-aggregated by
 * the persistence adapter via live {@code COUNT} queries against
 * {@code physical_capacity_assignments}/{@code physical_capacity_holds} at
 * read time — never a cached counter that could drift under concurrent
 * writes. {@code availableSpots} is therefore always consistent with the
 * rows actually committed at the moment of the read, not a separately
 * maintained value that a concurrent writer could desync (mirrors {@code
 * VirtualCourse}'s pre-aggregated {@code moduleCount}/{@code lessonCount}).
 * This class never creates assignments or holds itself — that write path
 * belongs to the (future) checkout/capacity flow, not session management.</p>
 */
public final class PhysicalSession {

    private final SessionId id;
    private final CourseId courseId;
    private final Instant scheduledAt;
    private final int capacity;
    private final int assignedSpots;
    private final int activeCapacityHolds;
    private final SessionStatus status;
    private final String notes;

    public PhysicalSession(
        SessionId id, CourseId courseId, Instant scheduledAt,
        int capacity, int assignedSpots, int activeCapacityHolds, SessionStatus status, String notes
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
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.notes = notes;
    }

    /**
     * Creates a brand-new session: fresh {@link SessionId}, always {@code
     * SCHEDULED}, with zero assignments/holds since nothing could have been
     * assigned against an id that did not exist a moment ago.
     */
    public static PhysicalSession create(CourseId courseId, Instant scheduledAt, int capacity, String notes) {
        return new PhysicalSession(
            SessionId.generate(), courseId, scheduledAt, capacity, 0, 0, SessionStatus.SCHEDULED, notes
        );
    }

    /**
     * Floored at zero: a bug in a future write path that oversells a
     * session must never surface here as a negative, nonsensical count.
     */
    public int getAvailableSpots() {
        return Math.max(0, capacity - assignedSpots - activeCapacityHolds);
    }

    public PhysicalSession withSchedule(Instant newScheduledAt) {
        return new PhysicalSession(
            id, courseId, newScheduledAt, capacity, assignedSpots, activeCapacityHolds, status, notes
        );
    }

    public PhysicalSession withNotes(String newNotes) {
        return new PhysicalSession(
            id, courseId, scheduledAt, capacity, assignedSpots, activeCapacityHolds, status, newNotes
        );
    }

    /**
     * US-PHYSICAL-006 escenario 5: a session's capacity may never drop
     * below the spots already confirmed — that would represent an
     * impossible negative availability, not just an unusual business state.
     */
    public PhysicalSession withCapacity(int newCapacity) {
        if (newCapacity < assignedSpots) {
            throw new CapacityBelowAssignedException();
        }
        return new PhysicalSession(
            id, courseId, scheduledAt, newCapacity, assignedSpots, activeCapacityHolds, status, notes
        );
    }

    public PhysicalSession cancel() {
        return new PhysicalSession(
            id, courseId, scheduledAt, capacity, assignedSpots, activeCapacityHolds, SessionStatus.CANCELLED, notes
        );
    }

    public boolean hasOccurred(Instant now) {
        return scheduledAt.isBefore(now);
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

    public SessionStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}
