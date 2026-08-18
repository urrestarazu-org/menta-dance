package com.menta.billing.application.port.out;

import java.util.Optional;

/**
 * Cross-module read port toward Virtual/Physical's course catalog
 * (US-BILLING-001 — "límites arquitectónicos").
 *
 * <p>{@code billing_plan_courses} stores only {@code course_id} by value —
 * never a FK, never a JOIN into another module's schema. Resolving the
 * human-readable course name for a response is this port's job; today no
 * implementation of it exists (issues #40/#46 build the real Virtual/Physical
 * catalog ports Billing will eventually call through), so the wired adapter
 * is a placeholder — see {@code NotImplementedCourseCatalogPort}.</p>
 */
public interface CourseCatalogPort {

    /**
     * @return the course's display name, or empty if the course is unknown
     *     to the catalog (deleted, or the id is stale). Never throws for a
     *     missing course — only for an infrastructure failure.
     */
    Optional<String> courseName(String courseId);
}
