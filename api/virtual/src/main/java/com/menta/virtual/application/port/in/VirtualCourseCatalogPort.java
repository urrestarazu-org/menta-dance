package com.menta.virtual.application.port.in;

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
}
