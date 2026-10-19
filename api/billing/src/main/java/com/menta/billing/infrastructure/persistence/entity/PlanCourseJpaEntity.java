package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA persistence model for billing_plan_courses.
 *
 * <p>{@code courseId} is a reference by value into Virtual/Physical's UUID
 * space — deliberately NOT a foreign key: those tables belong to other
 * modules and Billing must never JOIN into their schema.</p>
 */
@Entity
@Table(name = "billing_plan_courses")
public class PlanCourseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "plan_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private String courseId;

    protected PlanCourseJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PlanCourseJpaEntity(UUID planId, String courseId) {
        this.planId = planId;
        this.courseId = courseId;
    }

    public Long getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getCourseId() {
        return courseId;
    }
}
