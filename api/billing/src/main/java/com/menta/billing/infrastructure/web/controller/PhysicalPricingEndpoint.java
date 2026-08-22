package com.menta.billing.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose domain exceptions and bean-validation failures
 * are mapped to RFC 9457 problems by {@link
 * PhysicalCoursePricingExceptionHandler}, instead of a hardcoded controller
 * allowlist. Mirrors {@code PublicBillingEndpoint} and Virtual's {@code
 * VirtualManagementEndpoint} — named without "Public" since the {@code PUT}
 * side requires authentication, even though {@code GET} does not
 * (US-BILLING-009).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PhysicalPricingEndpoint {
}
