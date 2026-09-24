package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.AttendanceHistoryRow;
import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceSessionView;
import com.menta.physical.application.dto.AttendanceStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure static assembler (#39, US-PHYSICAL-002, design C6) — no mocks needed to reach the 0.95
 * domain+application coverage gate, mirrors {@code CourseProgressAssembler} in
 * {@code api/virtual}.
 *
 * <p>{@code includeAbsent} is applied ONLY to the returned {@code sessions()} list (D4); every
 * aggregate number is always computed over the full row set, which the caller must already have
 * assembled from the unrestricted or course-scoped query — this class never re-derives scope.</p>
 */
public final class MonthlyAttendanceAssembler {

    private static final int SCALE = 2;

    private MonthlyAttendanceAssembler() {
    }

    public static AttendanceHistoryView assemble(String period, List<AttendanceHistoryRow> rows, boolean includeAbsent) {
        int scheduledSessionCount = rows.size();
        int attended = 0;
        List<AttendanceSessionView> sessions = new ArrayList<>();
        for (AttendanceHistoryRow row : rows) {
            boolean rowAttended = row.attended();
            if (rowAttended) {
                attended++;
            }
            if (rowAttended || includeAbsent) {
                sessions.add(new AttendanceSessionView(
                    row.sessionId(), row.scheduledAt(), row.courseName(), row.instructorName(),
                    rowAttended ? AttendanceStatus.ATTENDED : AttendanceStatus.ABSENT, row.recordedAt()
                ));
            }
        }
        int absent = scheduledSessionCount - attended;
        return new AttendanceHistoryView(
            period, scheduledSessionCount, attended, absent, rateOf(attended, scheduledSessionCount), sessions
        );
    }

    /**
     * Multiply BEFORE dividing (design C6/Risk #5): dividing first at scale 2 and then
     * multiplying rounds twice — 2/3 would yield {@code 0.67 x 100 = 67.00} instead of the
     * correct {@code 66.67}.
     */
    private static BigDecimal rateOf(int attended, int scheduledSessionCount) {
        if (scheduledSessionCount == 0) {
            return new BigDecimal("0.00");
        }
        return BigDecimal.valueOf((long) attended * 100L)
            .divide(BigDecimal.valueOf(scheduledSessionCount), SCALE, RoundingMode.HALF_UP);
    }
}
