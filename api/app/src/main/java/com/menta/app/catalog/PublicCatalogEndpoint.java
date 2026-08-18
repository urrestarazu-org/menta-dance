package com.menta.app.catalog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose domain exceptions are mapped to RFC 9457
 * problems by {@link CatalogExceptionHandler}, instead of a hardcoded
 * controller allowlist. Mirrors billing's {@code PublicBillingEndpoint}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PublicCatalogEndpoint {
}
