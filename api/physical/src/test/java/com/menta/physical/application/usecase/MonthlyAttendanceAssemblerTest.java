package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.application.dto.AttendanceHistoryRow;
import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure, zero mocks (#39, US-PHYSICAL-002, design C6) — mirrors {@code CourseProgressAssemblerTest}
 * in {@code api/virtual}.
 */
class MonthlyAttendanceAssemblerTest {

    private static AttendanceHistoryRow attendedRow(Instant scheduledAt) {
        return new AttendanceHistoryRow(
            UUID.randomUUID(), scheduledAt, "Salsa Intermedio", "Ana Perez",
            scheduledAt.minusSeconds(300)
        );
    }

    private static AttendanceHistoryRow absentRow(Instant scheduledAt) {
        return new AttendanceHistoryRow(UUID.randomUUID(), scheduledAt, "Salsa Intermedio", "Ana Perez", null);
    }

    @Test
    void four_session_month_all_attended_yields_full_rate_and_no_absent_rows() {
        List<AttendanceHistoryRow> rows = List.of(
            attendedRow(Instant.parse("2026-10-02T22:00:00Z")),
            attendedRow(Instant.parse("2026-10-09T22:00:00Z")),
            attendedRow(Instant.parse("2026-10-16T22:00:00Z")),
            attendedRow(Instant.parse("2026-10-23T22:00:00Z"))
        );

        AttendanceHistoryView view = MonthlyAttendanceAssembler.assemble("2026-10", rows, true);

        assertThat(view.scheduledSessionCount()).isEqualTo(4);
        assertThat(view.attended()).isEqualTo(4);
        assertThat(view.absent()).isEqualTo(0);
        assertThat(view.attendanceRate()).isEqualByComparingTo("100.00");
        assertThat(view.sessions()).hasSize(4);
    }

    @Test
    void five_session_month_four_attended_one_absent_yields_eighty_percent_rate() {
        List<AttendanceHistoryRow> rows = List.of(
            attendedRow(Instant.parse("2026-09-03T22:00:00Z")),
            attendedRow(Instant.parse("2026-09-10T22:00:00Z")),
            attendedRow(Instant.parse("2026-09-17T22:00:00Z")),
            attendedRow(Instant.parse("2026-09-24T22:00:00Z")),
            absentRow(Instant.parse("2026-09-28T22:00:00Z"))
        );

        AttendanceHistoryView view = MonthlyAttendanceAssembler.assemble("2026-09", rows, true);

        assertThat(view.scheduledSessionCount()).isEqualTo(5);
        assertThat(view.attended()).isEqualTo(4);
        assertThat(view.absent()).isEqualTo(1);
        assertThat(view.attendanceRate()).isEqualByComparingTo("80.00");
    }

    /** design C6/Risk #5 — multiply-by-100-before-dividing, pinned as its own case. */
    @Test
    void two_of_three_attended_rounds_half_up_to_66_67_not_67_00() {
        List<AttendanceHistoryRow> rows = List.of(
            attendedRow(Instant.parse("2026-11-04T22:00:00Z")),
            attendedRow(Instant.parse("2026-11-11T22:00:00Z")),
            absentRow(Instant.parse("2026-11-18T22:00:00Z"))
        );

        AttendanceHistoryView view = MonthlyAttendanceAssembler.assemble("2026-11", rows, true);

        assertThat(view.attendanceRate()).isEqualByComparingTo(new BigDecimal("66.67"));
    }

    @Test
    void zero_denominator_yields_zero_rate() {
        AttendanceHistoryView view = MonthlyAttendanceAssembler.assemble("2026-12", List.of(), true);

        assertThat(view.scheduledSessionCount()).isEqualTo(0);
        assertThat(view.attended()).isEqualTo(0);
        assertThat(view.absent()).isEqualTo(0);
        assertThat(view.attendanceRate()).isEqualByComparingTo("0.00");
        assertThat(view.sessions()).isEmpty();
    }

    @Test
    void include_absent_false_hides_absent_rows_but_keeps_aggregates_identical() {
        List<AttendanceHistoryRow> rows = List.of(
            attendedRow(Instant.parse("2026-09-03T22:00:00Z")),
            attendedRow(Instant.parse("2026-09-10T22:00:00Z")),
            attendedRow(Instant.parse("2026-09-17T22:00:00Z")),
            attendedRow(Instant.parse("2026-09-24T22:00:00Z")),
            absentRow(Instant.parse("2026-09-28T22:00:00Z"))
        );

        AttendanceHistoryView withAbsent = MonthlyAttendanceAssembler.assemble("2026-09", rows, true);
        AttendanceHistoryView withoutAbsent = MonthlyAttendanceAssembler.assemble("2026-09", rows, false);

        assertThat(withAbsent.sessions()).hasSize(5);
        assertThat(withoutAbsent.sessions()).hasSize(4);
        assertThat(withoutAbsent.sessions())
            .noneMatch(session -> session.status() == AttendanceStatus.ABSENT);
        assertThat(withoutAbsent.scheduledSessionCount()).isEqualTo(withAbsent.scheduledSessionCount());
        assertThat(withoutAbsent.attended()).isEqualTo(withAbsent.attended());
        assertThat(withoutAbsent.absent()).isEqualTo(withAbsent.absent());
        assertThat(withoutAbsent.attendanceRate()).isEqualByComparingTo(withAbsent.attendanceRate());
    }

    @Test
    void absent_row_maps_to_null_recorded_at_and_attended_row_carries_it() {
        AttendanceHistoryRow attended = attendedRow(Instant.parse("2026-09-03T22:00:00Z"));
        AttendanceHistoryRow absent = absentRow(Instant.parse("2026-09-10T22:00:00Z"));

        AttendanceHistoryView view = MonthlyAttendanceAssembler.assemble("2026-09", List.of(attended, absent), true);

        assertThat(view.sessions().get(0).status()).isEqualTo(AttendanceStatus.ATTENDED);
        assertThat(view.sessions().get(0).recordedAt()).isEqualTo(attended.recordedAt());
        assertThat(view.sessions().get(1).status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(view.sessions().get(1).recordedAt()).isNull();
    }
}
