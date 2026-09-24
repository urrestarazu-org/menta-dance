package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import java.time.YearMonth;

/** In-port for a calendar-month attendance history read (#39, US-PHYSICAL-002). */
public interface GetPhysicalAttendanceHistoryUseCase {

    AttendanceHistoryView history(AttendanceViewer viewer, YearMonth month, boolean includeAbsent);
}
