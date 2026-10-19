package com.menta.virtual.infrastructure.persistence.entity;

import com.menta.virtual.domain.model.CourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for the virtual_courses table. */
@Entity
@Table(name = "virtual_courses")
public class VirtualCourseJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "short_description", nullable = false)
    private String shortDescription;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "professor_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID professorId;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "level", nullable = false, columnDefinition = "VARCHAR(20)")
    private String level;

    @Column(name = "is_premium", nullable = false)
    private boolean premium;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private CourseStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VirtualCourseJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public VirtualCourseJpaEntity(
        UUID id, String title, String shortDescription, String description, UUID professorId, String imageUrl,
        String category, String level, boolean premium, CourseStatus status, Instant createdAt, Instant updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.shortDescription = shortDescription;
        this.description = description;
        this.professorId = professorId;
        this.imageUrl = imageUrl;
        this.category = category;
        this.level = level;
        this.premium = premium;
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

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getCategory() {
        return category;
    }

    public String getLevel() {
        return level;
    }

    public boolean isPremium() {
        return premium;
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
