package com.menta.physical.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A recurring physical (in-person) course (US-PHYSICAL-003, US-PHYSICAL-005).
 *
 * <p>{@code professorId} is an opaque reference to an {@code api:auth} user
 * id — stored by value, never a FK or JOIN across modules
 * (docs/25-ARCHITECTURE-RULES.md). It is the ownership check's source of
 * truth: an INSTRUCTOR may only manage a course where {@code professorId}
 * equals their own authenticated user id.</p>
 */
public final class PhysicalCourse {

    private final CourseId id;
    private final String title;
    private final String description;
    private final UUID professorId;
    private final String professorName;
    private final DayOfWeek dayOfWeek;
    private final LocalTime startTime;
    private final int durationMinutes;
    private final PhysicalCourseLevel level;
    private final int capacity;
    private final CourseStatus status;

    public PhysicalCourse(
        CourseId id, String title, String description, UUID professorId, String professorName,
        DayOfWeek dayOfWeek, LocalTime startTime, int durationMinutes, PhysicalCourseLevel level,
        int capacity, CourseStatus status
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.professorId = Objects.requireNonNull(professorId, "professorId cannot be null");
        this.professorName = Objects.requireNonNull(professorName, "professorName cannot be null");
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek cannot be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime cannot be null");
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        this.durationMinutes = durationMinutes;
        this.level = Objects.requireNonNull(level, "level cannot be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    /** Creates a brand-new course: fresh {@link CourseId}, always {@code ACTIVE}. */
    public static PhysicalCourse create(
        String title, String description, UUID professorId, String professorName, DayOfWeek dayOfWeek,
        LocalTime startTime, int durationMinutes, PhysicalCourseLevel level, int capacity
    ) {
        return new PhysicalCourse(
            CourseId.generate(), title, description, professorId, professorName, dayOfWeek, startTime,
            durationMinutes, level, capacity, CourseStatus.ACTIVE
        );
    }

    public boolean isOwnedBy(UUID candidateProfessorId) {
        return professorId.equals(candidateProfessorId);
    }

    public boolean isActive() {
        return status == CourseStatus.ACTIVE;
    }

    public PhysicalCourse withTitle(String newTitle) {
        return new PhysicalCourse(
            id, newTitle, description, professorId, professorName, dayOfWeek, startTime, durationMinutes,
            level, capacity, status
        );
    }

    public PhysicalCourse withDescription(String newDescription) {
        return new PhysicalCourse(
            id, title, newDescription, professorId, professorName, dayOfWeek, startTime, durationMinutes,
            level, capacity, status
        );
    }

    /**
     * Rescheduling touches all three fields together — a course's slot is
     * meaningless with only one or two of them changed.
     */
    public PhysicalCourse withSchedule(DayOfWeek newDayOfWeek, LocalTime newStartTime, int newDurationMinutes) {
        return new PhysicalCourse(
            id, title, description, professorId, professorName, newDayOfWeek, newStartTime,
            newDurationMinutes, level, capacity, status
        );
    }

    public PhysicalCourse withLevel(PhysicalCourseLevel newLevel) {
        return new PhysicalCourse(
            id, title, description, professorId, professorName, dayOfWeek, startTime, durationMinutes,
            newLevel, capacity, status
        );
    }

    /**
     * Only the recurring course's own capacity changes. Sessions already
     * created (US-PHYSICAL-006) keep their own stored capacity — this never
     * cascades, so already-scheduled sessions are unaffected.
     */
    public PhysicalCourse withCapacity(int newCapacity) {
        return new PhysicalCourse(
            id, title, description, professorId, professorName, dayOfWeek, startTime, durationMinutes,
            level, newCapacity, status
        );
    }

    public PhysicalCourse withStatus(CourseStatus newStatus) {
        return new PhysicalCourse(
            id, title, description, professorId, professorName, dayOfWeek, startTime, durationMinutes,
            level, capacity, newStatus
        );
    }

    public CourseId getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public String getProfessorName() {
        return professorName;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public PhysicalCourseLevel getLevel() {
        return level;
    }

    public int getCapacity() {
        return capacity;
    }

    public CourseStatus getStatus() {
        return status;
    }
}
