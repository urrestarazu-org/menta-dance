package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.AttendanceHistoryView;
import java.math.BigDecimal;
import java.util.List;

/**
 * #39, US-PHYSICAL-002, design C6 — one shared response shape used by both the self and elevated
 * controllers, deliberately: the endpoints differ in WHO may call, never in what comes back (the
 * instructor narrowing already happened in the query, C1), and a shape difference between them
 * would itself be an enumeration oracle (C4).
 */
public record AttendanceHistoryResponse(
    String period, int scheduledSessionCount, int attended, int absent,
    BigDecimal attendanceRate, List<AttendanceSessionResponse> sessions
) {

    public static AttendanceHistoryResponse from(AttendanceHistoryView view) {
        return new AttendanceHistoryResponse(
            view.period(), view.scheduledSessionCount(), view.attended(), view.absent(), view.attendanceRate(),
            view.sessions().stream().map(AttendanceSessionResponse::from).toList()
        );
    }
}
