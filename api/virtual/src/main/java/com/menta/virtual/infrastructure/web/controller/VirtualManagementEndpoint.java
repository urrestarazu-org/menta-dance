package com.menta.virtual.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose domain exceptions and bean-validation failures are
 * mapped to RFC 9457 problems by {@link VirtualCourseExceptionHandler},
 * instead of a hardcoded controller allowlist. Mirrors {@code physical}'s
 * {@code PhysicalManagementEndpoint} — named without "Public" because these
 * endpoints require authentication (US-VIRTUAL-006).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface VirtualManagementEndpoint {
}
