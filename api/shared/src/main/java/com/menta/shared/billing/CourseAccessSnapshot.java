package com.menta.shared.billing;

/**
 * Billing-owned facts Virtual needs to apply its local lesson-access policy.
 *
 * <p>The values are intentionally facts rather than a grant: Virtual first
 * evaluates its own free and preview rules, then treats {@code currentEntitlement}
 * as the only paid-access fact for a protected course.</p>
 */
public record CourseAccessSnapshot(boolean courseInAnyPlan, boolean currentEntitlement) {
}
