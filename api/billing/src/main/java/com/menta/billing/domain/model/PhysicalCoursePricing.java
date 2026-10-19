package com.menta.billing.domain.model;

import com.menta.billing.domain.exception.InvalidIndividualSurchargeException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A physical course's versioned pricing configuration (US-BILLING-009).
 *
 * <p>{@code courseId} is an opaque reference into Physical's course space —
 * stored by value, never a FK or JOIN across modules
 * (docs/25-ARCHITECTURE-RULES.md), same discipline as {@link PlanCourse}.
 * Every change produces a brand-new instance with an incremented {@code
 * version}: existing quotes/Purchases keep referencing their own already
 * computed snapshot, never this live record (escenario 3).</p>
 */
public final class PhysicalCoursePricing {

    private static final int FIRST_VERSION = 1;

    private final String courseId;
    private final Money monthlyPrice;
    private final BigDecimal individualSurchargePercent;
    private final int version;
    private final Instant updatedAt;

    private PhysicalCoursePricing(
        String courseId, Money monthlyPrice, BigDecimal individualSurchargePercent, int version, Instant updatedAt
    ) {
        this.courseId = Objects.requireNonNull(courseId, "courseId cannot be null");
        this.monthlyPrice = Objects.requireNonNull(monthlyPrice, "monthlyPrice cannot be null");
        if (individualSurchargePercent == null || individualSurchargePercent.signum() <= 0) {
            // Escenario 2: a surcharge <= 0 must reject before anything is persisted.
            throw new InvalidIndividualSurchargeException();
        }
        this.individualSurchargePercent = individualSurchargePercent;
        this.version = version;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    /** First-ever pricing published for a course — always version 1. */
    public static PhysicalCoursePricing createFirstVersion(
        String courseId, Money monthlyPrice, BigDecimal individualSurchargePercent, Instant updatedAt
    ) {
        return new PhysicalCoursePricing(courseId, monthlyPrice, individualSurchargePercent, FIRST_VERSION, updatedAt);
    }

    /** Reconstructs a pricing already persisted at a known version — used by the repository adapter. */
    public static PhysicalCoursePricing reconstitute(
        String courseId, Money monthlyPrice, BigDecimal individualSurchargePercent, int version, Instant updatedAt
    ) {
        return new PhysicalCoursePricing(courseId, monthlyPrice, individualSurchargePercent, version, updatedAt);
    }

    /**
     * Publishes a new version on top of this one. Never mutates {@code
     * this} — the caller (the use case) still holds the previous version to
     * build the audit revision's {@code previousValue} snapshot.
     */
    public PhysicalCoursePricing revise(Money newMonthlyPrice, BigDecimal newIndividualSurchargePercent, Instant now) {
        return new PhysicalCoursePricing(courseId, newMonthlyPrice, newIndividualSurchargePercent, version + 1, now);
    }

    public String getCourseId() {
        return courseId;
    }

    public Money getMonthlyPrice() {
        return monthlyPrice;
    }

    public BigDecimal getIndividualSurchargePercent() {
        return individualSurchargePercent;
    }

    public int getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
