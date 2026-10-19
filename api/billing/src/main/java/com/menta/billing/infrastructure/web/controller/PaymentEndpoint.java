package com.menta.billing.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose domain exceptions and bean-validation failures are mapped to RFC 9457
 * problems by {@link PaymentExceptionHandler} — mirrors {@link SubscriptionEndpoint} (#31,
 * US-BILLING-003).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PaymentEndpoint {
}
