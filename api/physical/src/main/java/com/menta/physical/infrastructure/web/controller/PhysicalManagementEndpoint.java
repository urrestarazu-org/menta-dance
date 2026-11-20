package com.menta.physical.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose domain exceptions are mapped to RFC 9457 problems
 * by {@link PhysicalCourseExceptionHandler}, and whose bean-validation and
 * body-decoding failures by {@link PhysicalRequestExceptionHandler}, instead
 * of a hardcoded controller allowlist. Mirrors billing's {@code
 * PublicBillingEndpoint} — named without "Public" because these endpoints
 * require authentication (US-PHYSICAL-005).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PhysicalManagementEndpoint {
}
