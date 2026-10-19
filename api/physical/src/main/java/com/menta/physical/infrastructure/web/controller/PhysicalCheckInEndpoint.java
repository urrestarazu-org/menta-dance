package com.menta.physical.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks {@link PhysicalCheckInController}, whose domain exceptions and
 * bean-validation failures are mapped to RFC 9457 problems by
 * {@link PhysicalCheckInExceptionHandler} (US-PHYSICAL-001).
 *
 * <p>Deliberately a separate marker from {@link PhysicalManagementEndpoint},
 * not a reuse of it: that one is scoped to ADMIN/INSTRUCTOR management
 * endpoints, while check-in mixes an authenticated-student endpoint
 * ({@code access-qr}) with a {@code permitAll()} device endpoint
 * ({@code check-ins}) whose authorization lives entirely inside the use
 * case. Same criterion billing uses to keep {@code WebhookExceptionHandler}
 * separate from {@code PlanExceptionHandler}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PhysicalCheckInEndpoint {
}
