package com.menta.virtual.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose domain exceptions are mapped to RFC 9457
 * problems by {@link VirtualPublicLessonExceptionHandler}. Equivalent of
 * {@code api:app}'s {@code PublicCatalogEndpoint} and the billing module's
 * {@code PublicBillingEndpoint}: a type-level marker so the
 * {@code @RestControllerAdvice} selector picks only the public endpoints
 * — never the auth-only {@code VirtualManagementEndpoint} advice on the
 * same module — without an allowlist in {@code VirtualConfiguration}.
 *
 * <p>The two advice chains need to be separate because their
 * {@link IllegalArgumentException} policy is different: the management
 * one collapses malformed path / body fields into a 400 with a
 * human-readable detail; the public one MUST collapse them into the
 * same 404 response so an attacker cannot enumerate UUID formats. A
 * single shared advice would have to pick one of the two policies, and
 * neither is correct.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PublicVirtualEndpoint {
}
