package com.menta.physical.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the student-facing controllers that read over a UTC instant range (attendance history, #39,
 * US-PHYSICAL-002, and the student's own sessions, #356, #729), whose malformed-parameter and range
 * exceptions are mapped to RFC 9457 problems by {@link PhysicalAttendanceExceptionHandler}.
 *
 * <p>A separate marker from {@link PhysicalCheckInEndpoint}/{@link PhysicalManagementEndpoint}:
 * same criterion those two already establish — this endpoint's exception surface (malformed
 * range/{@code studentId}) is distinct from either.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PhysicalAttendanceEndpoint {
}
