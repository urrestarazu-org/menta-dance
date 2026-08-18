package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Entry point Physical exposes for other modules to read recurring courses
 * and scheduled-session availability (US-PHYSICAL-003). {@code api:app}'s
 * catalog composition (#95) calls this directly — same cross-module pattern
 * as {@code VirtualCourseCatalogPort} and {@code
 * docs/27-CLEAN-ARCHITECTURE-GUIDE.md}'s {@code UserQueryPort} example: a
 * Java interface, never HTTP, RabbitMQ or a shared schema.
 *
 * <p>This module never exposes its own public HTTP endpoint for the
 * catalog, nor any reservation/waitlist endpoint — both are out of scope
 * for this issue. Pricing is never exposed here either; that is Billing's
 * job via {@code POST /api/v1/billing/physical/quotes}.</p>
 */
public interface PhysicalCourseAvailabilityPort {

    /**
     * @param afterCursor {@code null} for the first page; otherwise the
     *     opaque {@code courseId} of the last course seen on the previous
     *     page.
     * @param pageSize maximum number of courses to return.
     * @return active recurring courses only, or an empty list if none are active.
     */
    List<PhysicalCourseSummary> listCourses(String afterCursor, int pageSize);

    /**
     * Single-course lookup for {@code api:app}'s catalog detail endpoint
     * (#95) — {@code GET /api/v1/catalog/courses/{courseId}} does not know
     * the modality in advance, so it resolves against this and Virtual's
     * equivalent port and keeps whichever responds.
     *
     * @param courseId the opaque course id to look up.
     * @return the course if it exists and is {@code ACTIVE}; {@code
     *     Optional.empty()} both when it does not exist and when it exists
     *     but is inactive — the same non-enumeration discipline {@code
     *     listCourses} already applies, so a caller can never tell the two
     *     cases apart.
     */
    Optional<PhysicalCourseSummary> findActiveById(String courseId);

    /**
     * @param courseId the recurring course whose scheduled sessions to list.
     * @param from lower bound (inclusive) of the schedule range.
     * @param to upper bound (exclusive) of the schedule range.
     * @return sessions scheduled for {@code courseId} within
     *     {@code [from, to)}, each with its live-computed availability, or
     *     an empty list if none are scheduled in that range.
     */
    List<PhysicalSessionAvailability> listSessions(String courseId, Instant from, Instant to);
}
