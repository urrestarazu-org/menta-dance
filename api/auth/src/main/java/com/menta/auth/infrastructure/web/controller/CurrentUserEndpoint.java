package com.menta.auth.infrastructure.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose domain exceptions are mapped to RFC 9457 problems by
 * {@link CurrentUserExceptionHandler}, instead of a hardcoded controller allowlist.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CurrentUserEndpoint {
}
