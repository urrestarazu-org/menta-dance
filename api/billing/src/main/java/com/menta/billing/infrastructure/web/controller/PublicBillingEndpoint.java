package com.menta.billing.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose domain exceptions and bean-validation failures are
 * mapped to RFC 9457 problems by {@link PlanExceptionHandler}, instead of a
 * hardcoded controller allowlist. Mirrors auth's {@code
 * PublicActivationEndpoint}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PublicBillingEndpoint {
}
