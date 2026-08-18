package com.menta.physical.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A recurring physical (in-person) course (US-PHYSICAL-003).
 *
 * <p>Read-only for now: this issue only lists active recurring courses, it
 * never creates or edits one, and it never calculates or stores price —
 * pricing lives in Billing ({@code POST /api/v1/billing/physical/quotes}).</p>
 */
public final class PhysicalCourse {

    private final CourseId id;
    private final String title;
    private final String professorName;
    private final DayOfWeek dayOfWeek;
    private final LocalTime startTime;
    private final PhysicalCourseLevel level;
    private final int capacity;
    private final CourseStatus status;

    public PhysicalCourse(
        CourseId id, String title, String professorName, DayOfWeek dayOfWeek,
        LocalTime startTime, PhysicalCourseLevel level, int capacity, CourseStatus status
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.professorName = Objects.requireNonNull(professorName, "professorName cannot be null");
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek cannot be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime cannot be null");
        this.level = Objects.requireNonNull(level, "level cannot be null");
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity cannot be negative");
        }
        this.capacity = capacity;
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public boolean isActive() {
        return status == CourseStatus.ACTIVE;
    }

    public CourseId getId() {
        return id;
    }

    public String getTitle() {
        return title;
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
