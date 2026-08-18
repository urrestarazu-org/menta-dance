package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.VirtualCourseSummary;
import java.util.List;
import java.util.Optional;

/**
 * Entry point Virtual exposes for other modules to read its published
 * catalog (US-VIRTUAL-001). {@code api:app}'s catalog composition (#95)
 * calls this directly — same cross-module pattern as
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
}
