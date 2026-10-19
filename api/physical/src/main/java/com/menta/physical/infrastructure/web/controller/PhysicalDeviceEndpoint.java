package com.menta.physical.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks {@link PhysicalDeviceAdminController}, whose exceptions are mapped to RFC 9457 problems by
 * {@link PhysicalDeviceExceptionHandler} (#44, US-PHYSICAL-007, design C6).
 *
 * <p>A separate marker/advice pair from {@link PhysicalManagementEndpoint}/{@link
 * PhysicalAttendanceEndpoint}, mirroring how #39 introduced its own {@code
 * PhysicalAttendanceEndpoint} rather than extending an existing advice: this endpoint's exception
 * surface — {@code DeviceAlreadyRevokedException}/{@code DeviceRevokedException} mapped to {@code
 * 409} — is distinct from either.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PhysicalDeviceEndpoint {
}
