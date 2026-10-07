package com.menta.shared.demo;

/**
 * Execution order of the demo seed runners across modules.
 *
 * <p>The values are plain {@code int}s (relative to {@code Ordered.HIGHEST_PRECEDENCE}) so this module keeps
 * no Spring dependency. Authentication users come first, catalog content second and billing last, because
 * billing refers to users and courses created by the earlier runners.
 */
public final class DemoSeedOrder {

    /** Same value as {@code org.springframework.core.Ordered.HIGHEST_PRECEDENCE}. */
    private static final int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

    /** Users (Auth). */
    public static final int AUTH = HIGHEST_PRECEDENCE + 100;

    /** Courses, sessions and devices (Virtual and Physical). */
    public static final int CONTENT = HIGHEST_PRECEDENCE + 200;

    /** Plans, prices and subscriptions (Billing). */
    public static final int BILLING = HIGHEST_PRECEDENCE + 300;

    private DemoSeedOrder() {
    }
}
