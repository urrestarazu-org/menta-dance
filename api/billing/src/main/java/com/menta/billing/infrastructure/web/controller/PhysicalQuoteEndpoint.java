package com.menta.billing.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose domain exceptions and bean-validation failures
 * are mapped to RFC 9457 problems by {@link PhysicalCourseQuoteExceptionHandler},
 * mirrors {@code PhysicalPricingEndpoint} (US-BILLING-006).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PhysicalQuoteEndpoint {
}
