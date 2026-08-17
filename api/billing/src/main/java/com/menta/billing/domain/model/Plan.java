package com.menta.billing.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A subscription plan (US-BILLING-001).
 *
 * <p>Read-only for now: this issue only lists and shows plans, it never
 * creates or edits one. Immutable by construction rather than by convention
 * — there is no mutator to forget to call.</p>
 */
public final class Plan {

    private final PlanId id;
    private final String name;
    private final String description;
    private final Money price;
    private final int durationDays;
    private final boolean featured;
    private final PlanStatus status;
    private final String termsAndConditions;
    private final String cancellationPolicy;
    private final List<PlanCourse> courses;

    public Plan(
        PlanId id, String name, String description, Money price, int durationDays,
        boolean featured, PlanStatus status, String termsAndConditions,
        String cancellationPolicy, List<PlanCourse> courses
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.price = Objects.requireNonNull(price, "price cannot be null");
        if (durationDays <= 0) {
            throw new IllegalArgumentException("durationDays must be positive");
        }
        this.durationDays = durationDays;
        this.featured = featured;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.termsAndConditions = Objects.requireNonNull(termsAndConditions, "termsAndConditions cannot be null");
        this.cancellationPolicy = Objects.requireNonNull(cancellationPolicy, "cancellationPolicy cannot be null");
        this.courses = List.copyOf(Objects.requireNonNull(courses, "courses cannot be null"));
    }

    public boolean isActive() {
        return status == PlanStatus.ACTIVE;
    }

    public PlanId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public boolean isFeatured() {
        return featured;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public String getTermsAndConditions() {
        return termsAndConditions;
    }

    public String getCancellationPolicy() {
        return cancellationPolicy;
    }

    public List<PlanCourse> getCourses() {
        return courses;
    }
}
