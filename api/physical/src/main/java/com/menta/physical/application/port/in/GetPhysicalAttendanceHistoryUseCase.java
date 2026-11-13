package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import java.time.Instant;

/** In-port for an attendance history read over an instant range (#39, #729, US-PHYSICAL-002). */
public interface GetPhysicalAttendanceHistoryUseCase {

    /**
     * Builds a student's attendance summary for the sessions whose start is in the half-open range
     * {@code [from, to)}. Both bounds are UTC instants: the caller converts its local month to the
     * range, the backend never applies a time zone.
     *
     * <p>The {@code viewer} decides the scope: self and admin readers see every assignment,
     * instructor readers only the sessions of courses they own, and that narrowing happens in the
     * query. {@code includeAbsent} trims only the returned session list; the counts and the rate
     * always cover every scheduled session. An unknown student yields the same empty view as one
     * with no sessions.
     *
     * @throws com.menta.physical.domain.exception.InvalidSessionRangeException when {@code from} is not
     *     before {@code to} or the range spans more than 92 days
     */
    AttendanceHistoryView history(AttendanceViewer viewer, Instant from, Instant to, boolean includeAbsent);
}
