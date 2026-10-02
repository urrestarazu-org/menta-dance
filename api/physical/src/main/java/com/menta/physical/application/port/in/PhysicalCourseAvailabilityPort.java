package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
     * Batch counterpart of {@link #findActiveById(String)}: same visibility
     * ({@code ACTIVE} only) and same summary, resolved with a single query
     * regardless of how many ids are requested.
     *
     * <p>Null, blank and non-UUID ids are skipped without a query and never
     * fail the batch (unlike the single-id lookup, which propagates
     * {@link IllegalArgumentException}). Duplicate ids are looked up once.</p>
     *
     * @param courseIds the opaque course ids to look up; must not be
     *     {@code null} (a {@code null} collection throws {@link
     *     NullPointerException}).
     * @return an immutable map keyed by the id string exactly as the caller
     *     passed it (so an uppercase UUID is answered under its uppercase
     *     key, while {@code summary.courseId()} stays canonical), containing
     *     only the courses that exist and are {@code ACTIVE}. Unknown and
     *     inactive ids are omitted without distinguishing them.
     */
    Map<String, PhysicalCourseSummary> findActiveByIds(Collection<String> courseIds);

    /**
     * Lists the {@code SCHEDULED} sessions of a course in the half-open range
     * {@code [from, to)}: a session exactly at {@code from} is included, one
     * exactly at {@code to} is not. Cancelled sessions never appear. The
     * result is ordered by {@code scheduledAt} ascending, so callers that
     * truncate the list keep the earliest sessions.
     *
     * @param courseId the recurring course whose scheduled sessions to list.
     * @param from lower bound (inclusive) of the schedule range.
     * @param to upper bound (exclusive) of the schedule range.
     * @return sessions scheduled for {@code courseId} within
     *     {@code [from, to)}, ordered by {@code scheduledAt} ascending, each
     *     with its live-computed availability, or an empty list if none are
     *     scheduled in that range.
     */
    List<PhysicalSessionAvailability> listSessions(String courseId, Instant from, Instant to);
}
