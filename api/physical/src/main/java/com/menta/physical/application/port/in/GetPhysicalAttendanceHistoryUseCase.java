package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import java.time.YearMonth;

/** In-port for a calendar-month attendance history read (#39, US-PHYSICAL-002). */
public interface GetPhysicalAttendanceHistoryUseCase {

    /**
     * Builds a student's attendance summary for one calendar month.
     *
     * <p>The {@code viewer} decides the scope: self and admin readers see every assignment,
     * instructor readers only the sessions of courses they own, and that narrowing happens in the
     * query. {@code includeAbsent} trims only the returned session list; the counts and the rate
     * always cover every scheduled session. An unknown student yields the same empty view as one
     * with no sessions.
     */
    AttendanceHistoryView history(AttendanceViewer viewer, YearMonth month, boolean includeAbsent);
}
