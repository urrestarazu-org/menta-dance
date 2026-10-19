package com.menta.virtual.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose domain exceptions are mapped to RFC 9457 problems by
 * {@link VirtualStudentProgressExceptionHandler} (US-VIRTUAL-005). A third advice chain, separate
 * from {@link PublicVirtualEndpoint} and {@code VirtualManagementEndpoint}: the public chain
 * collapses a malformed id's {@code IllegalArgumentException} into the same anti-enumeration 404
 * used here, but neither existing chain has a branch for
 * {@link com.menta.virtual.domain.exception.InvalidLessonPositionException}'s dedicated 400.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface VirtualStudentEndpoint {
}
