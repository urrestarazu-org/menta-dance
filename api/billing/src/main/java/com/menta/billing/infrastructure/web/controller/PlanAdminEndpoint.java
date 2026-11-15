package com.menta.billing.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose bean-validation, body-decoding and domain-rule failures are mapped to
 * RFC 9457 problems by {@link PlanAdminExceptionHandler} (#762). Separate from {@link
 * PublicBillingEndpoint}: the public plan catalog has rate-limit and not-found mappings that do
 * not apply to plan administration, and vice versa.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PlanAdminEndpoint {
}
