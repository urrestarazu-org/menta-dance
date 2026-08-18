package com.menta.physical.application.port.out;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import java.time.Instant;
import java.util.List;

/**
 * Persistence port for {@link PhysicalSession}. Implementations MUST compute
 * {@code assignedSpots}/{@code activeCapacityHolds} via a live query against
 * committed rows at call time — never a cached counter (see {@link
 * PhysicalSession}'s Javadoc).
 */
public interface PhysicalSessionRepository {

    List<PhysicalSession> findScheduled(CourseId courseId, Instant from, Instant to);
}
