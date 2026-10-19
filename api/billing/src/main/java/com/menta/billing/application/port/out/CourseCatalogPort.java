package com.menta.billing.application.port.out;

import java.util.Collection;
import java.util.Map;

/**
 * Cross-module read port toward Virtual/Physical's course catalog
 * (US-BILLING-001 — "límites arquitectónicos").
 *
 * <p>{@code billing_plan_courses} stores only {@code course_id} by value —
 * never a FK, never a JOIN into another module's schema. Resolving the
 * human-readable course names for a response is this port's job, and it is
 * done in one batch per request so the cost does not grow with the number of
 * plans or courses. The wired adapter lives in {@code api:app} ({@code
 * com.menta.app.billing.CourseCatalogPortAdapter}) and composes Virtual's and
 * Physical's entry ports, virtual first — a plain Java call, never HTTP or a
 * shared schema (ADR-0037).</p>
 */
public interface CourseCatalogPort {

    /**
     * Resolves the display names of the given courses in one batch.
     *
     * <p>The result is immutable and contains only the resolved ids, keyed by
     * the exact id string of the input. An id that cannot be resolved
     * (unknown, not publicly visible, or not a valid course id) is simply
     * absent; it is never an error. Duplicate ids are looked up once. An
     * empty input returns an empty map without any lookup. The method throws
     * only for an infrastructure failure.</p>
     *
     * @param courseIds the course ids to resolve; must not be {@code null}
     * @return the resolved names keyed by input id, never {@code null}
     * @throws NullPointerException if {@code courseIds} is {@code null}
     */
    Map<String, String> courseNames(Collection<String> courseIds);
}
