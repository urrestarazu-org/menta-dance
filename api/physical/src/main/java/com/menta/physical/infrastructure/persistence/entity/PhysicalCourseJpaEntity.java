package com.menta.physical.infrastructure.persistence.entity;

import com.menta.physical.domain.model.CourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** JPA persistence model for the physical_courses table. */
@Entity
@Table(name = "physical_courses")
public class PhysicalCourseJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "professor_name", nullable = false)
    private String professorName;

    @Column(name = "day_of_week", nullable = false, columnDefinition = "VARCHAR(10)")
    private String dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "level", nullable = false, columnDefinition = "VARCHAR(20)")
    private String level;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private CourseStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PhysicalCourseJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PhysicalCourseJpaEntity(
        UUID id, String title, String professorName, String dayOfWeek, LocalTime startTime,
        String level, int capacity, CourseStatus status, Instant createdAt, Instant updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.professorName = professorName;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.level = level;
        this.capacity = capacity;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getProfessorName() {
        return professorName;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public String getLevel() {
        return level;
    }

    public int getCapacity() {
        return capacity;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
