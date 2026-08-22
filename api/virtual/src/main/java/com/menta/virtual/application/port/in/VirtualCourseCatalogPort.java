package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.VirtualCourseAdminDetailView;
import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.dto.VirtualCourseSummary;
import java.util.List;
import java.util.Optional;

/**
 * Entry point Virtual exposes for other modules to read its published
 * catalog (US-VIRTUAL-001, US-VIRTUAL-002). {@code api:app}'s catalog
 * composition (#95, #47) calls this directly — same cross-module pattern as
 * {@code docs/27-CLEAN-ARCHITECTURE-GUIDE.md}'s {@code UserQueryPort}
 * example: a Java interface, never HTTP, RabbitMQ or a shared schema.
 *
 * <p>This module never exposes its own public HTTP endpoint for the
 * catalog — the ONLY public route is {@code GET /api/v1/catalog/courses},
 * owned by {@code api:app}.</p>
 */
public interface VirtualCourseCatalogPort {

    /**
     * @param afterCursor {@code null} for the first page; otherwise the
     *     opaque {@code courseId} of the last course seen on the previous
     *     page.
     * @param pageSize maximum number of courses to return.
     * @return published courses only — never {@code DRAFT} or
     *     {@code ARCHIVED} — or an empty list if none are published.
     */
    List<VirtualCourseSummary> listPublished(String afterCursor, int pageSize);

    /**
     * Single-course lookup for {@code api:app}'s catalog detail endpoint
     * (#95) — {@code GET /api/v1/catalog/courses/{courseId}} does not know
     * the modality in advance, so it resolves against this and Physical's
     * equivalent port and keeps whichever responds.
     *
     * @param courseId the opaque course id to look up.
     * @return the course if it exists and is {@code PUBLISHED}; {@code
     *     Optional.empty()} both when it does not exist and when it exists
     *     but is not published — the same non-enumeration discipline
     *     {@code listPublished} already applies, so a caller can never tell
     *     the two cases apart.
     */
    Optional<VirtualCourseSummary> findPublishedById(String courseId);

    /**
     * Rich-detail lookup for {@code api:app}'s public course-detail endpoint
     * (#47, US-VIRTUAL-002 escenario 1). Adds module/lesson tree and
     * pre-aggregated stats on top of {@link #findPublishedById(String)};
     * lesson summaries NEVER expose {@code videoId} — see
     * {@link VirtualCourseDetailView}.
     *
     * <p>This method is intentionally separate from {@link
     * #findPublishedById(String)}: the list view is the hot path and stays
     * scalar-only, while the detail view pays the extra round-trips only
     * when a visitor drills into a specific course.</p>
     *
     * @param courseId the opaque course id to look up.
     * @return the rich detail if the course exists and is {@code PUBLISHED};
     *     {@code Optional.empty()} both when it does not exist and when it
     *     exists but is not published, mirroring {@link
     *     #findPublishedById(String)}'s non-enumeration discipline. A
     *     malformed {@code courseId} (not a UUID) is propagated as
     *     {@code IllegalArgumentException}; the composition layer in
     *     {@code api:app} treats that the same way as "not found" per #47
     *     US escenario 3.
     */
    Optional<VirtualCourseDetailView> findPublishedDetailById(String courseId);

    /**
     * Rich-detail lookup for the admin course-detail endpoint
     * (US-VIRTUAL-002 escenario 5, #125): {@code GET
     * /api/v1/admin/virtual/courses/{courseId}} hosted by {@code api:virtual}
     * itself — unlike the public catalog detail above, the admin route
     * lives in this module so the port serves the management layer
     * directly. Lesson summaries HERE expose {@code videoId} because the
     * operator needs Bunny.net references to wire the editor/UI; the public
     * port above never surfaces them.
     *
     * <p>This method DELIBERATELY inverts the non-enumeration discipline
     * used by the public port: the admin UI must be able to inspect
     * {@code DRAFT} and {@code ARCHIVED} courses (its users are themselves
     * authenticated operators and may legitimately be the only ones
     * allowed to see those rows), so {@code Optional.empty()} means ONLY
     * "the id does not resolve at all". A {@code DRAFT} or {@code ARCHIVED}
     * course returns a fully-populated {@link VirtualCourseAdminDetailView}
     * with {@code status} set — status is exposed because the operator
     * needs to act on it.</p>
     *
     * <p>A malformed {@code courseId} (not a UUID) is treated as
     * "non-existent" and yields {@code Optional.empty()}, NOT propagated as
     * an exception. This is a deliberate contract split from {@link
     * #findPublishedDetailById(String)} (which propagates the
     * {@link IllegalArgumentException} {@code CourseId.of} throws): the
     * admin caller in this module is always within the same exception
     * handler chain and already returns a 404 {@code ProblemDetail} on
     * empty, so collapsing the malformed-input case into the same response
     * keeps the admin UI contract uniform. Specifically: the admin
     * controller throws {@code CourseNotFoundException} on {@code
     * Optional.empty()}, mapped to {@code 404} by the @RestControllerAdvice
     * — there is no operator-facing benefit to distinguishing 400 from
     * 404 here, and forcing the admin UI to render a different error shape
     * for "wrong UUID" vs. "missing course" would be busywork.</p>
     *
     * @param courseId the opaque course id to look up.
     * @return the rich detail with {@code status}/{@code videoId}/etc.,
     *     regardless of status; {@code Optional.empty()} when the id does
     *     not resolve OR when it is not a valid UUID.
     */
    Optional<VirtualCourseAdminDetailView> findByIdForAdmin(String courseId);
}
