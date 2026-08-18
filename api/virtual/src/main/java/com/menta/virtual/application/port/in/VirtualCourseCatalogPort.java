package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.VirtualCourseSummary;
import java.util.List;

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
}
