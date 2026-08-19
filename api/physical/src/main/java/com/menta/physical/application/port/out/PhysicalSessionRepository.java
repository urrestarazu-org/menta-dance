package com.menta.physical.application.port.out;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link PhysicalSession}. Implementations MUST compute
 * {@code assignedSpots}/{@code activeCapacityHolds} via a live query against
 * committed rows at call time — never a cached counter (see {@link
 * PhysicalSession}'s Javadoc).
 */
public interface PhysicalSessionRepository {

    /**
     * Public-safe read: only {@code SCHEDULED} sessions (US-PHYSICAL-006).
     * A {@code CANCELLED} session must never be shown as available to an
     * external visitor — consumed by {@code PhysicalCourseAvailabilityPort}
     * (#40) and the composed public catalog (#95).
     */
    List<PhysicalSession> findScheduled(CourseId courseId, Instant from, Instant to);

    /**
     * Management read: every session in the range regardless of status —
     * an administrator/professor needs to see cancelled sessions too
     * (US-PHYSICAL-006 escenario 3).
     */
    List<PhysicalSession> findManaged(CourseId courseId, Instant from, Instant to);

    Optional<PhysicalSession> findById(SessionId sessionId);

    /** Creates a new session or updates an existing one (matched by {@link PhysicalSession#getId()}). */
    PhysicalSession save(PhysicalSession session);

    List<PhysicalSession> saveAll(List<PhysicalSession> sessions);

    /**
     * US-PHYSICAL-005 escenario 4: a course may not be deactivated while it
     * still has a future session with at least one confirmed assignment —
     * deactivating would silently strand those students.
     */
    boolean hasFutureAssignedSessions(CourseId courseId);
}
