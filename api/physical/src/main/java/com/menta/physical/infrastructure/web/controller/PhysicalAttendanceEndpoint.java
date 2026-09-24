package com.menta.physical.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the attendance-history controllers, whose malformed-parameter exceptions are mapped to
 * RFC 9457 problems by {@link PhysicalAttendanceExceptionHandler} (#39, US-PHYSICAL-002).
 *
 * <p>A separate marker from {@link PhysicalCheckInEndpoint}/{@link PhysicalManagementEndpoint}:
 * same criterion those two already establish — this endpoint's exception surface (malformed
 * {@code month}/{@code studentId}) is distinct from either.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PhysicalAttendanceEndpoint {
}
