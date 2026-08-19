package com.menta.virtual.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA persistence model for the virtual_modules table. */
@Entity
@Table(name = "virtual_modules")
public class VirtualModuleJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "course_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected VirtualModuleJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public VirtualModuleJpaEntity(UUID id, UUID courseId, String title, int displayOrder) {
        this.id = id;
        this.courseId = courseId;
        this.title = title;
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
