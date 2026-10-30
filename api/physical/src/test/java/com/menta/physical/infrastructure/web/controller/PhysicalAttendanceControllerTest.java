package com.menta.physical.infrastructure.web.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceSessionView;
import com.menta.physical.application.dto.AttendanceStatus;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.in.GetPhysicalAttendanceHistoryUseCase;
import com.menta.physical.domain.model.AttendanceKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** #39, US-PHYSICAL-002, design C7 — MockMvc slice, mirrors {@code PhysicalCourseQuoteControllerTest}. */
class PhysicalAttendanceControllerTest {

    private GetPhysicalAttendanceHistoryUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(GetPhysicalAttendanceHistoryUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalAttendanceController(useCase))
            .setControllerAdvice(new PhysicalAttendanceExceptionHandler())
            .build();
    }

    private static Authentication authOf(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    @Test
    void month_happy_path_returns_the_assembled_body_for_the_authenticated_student() throws Exception {
        UUID studentId = UUID.randomUUID();
        AttendanceHistoryView view =
            new AttendanceHistoryView("2026-09", 5, 4, 1, new BigDecimal("80.00"), List.of());
        when(useCase.history(eq(AttendanceViewer.self(studentId)), eq(YearMonth.of(2026, 9)), eq(false)))
            .thenReturn(view);

        mockMvc.perform(get("/api/v1/physical/attendance/me")
                .param("month", "2026-09")
                .principal(authOf(studentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scheduledSessionCount", is(5)))
            .andExpect(jsonPath("$.attended", is(4)))
            .andExpect(jsonPath("$.absent", is(1)))
            .andExpect(jsonPath("$.attendanceRate", is(80.00)));

        verify(useCase).history(AttendanceViewer.self(studentId), YearMonth.of(2026, 9), false);
    }

    /** Scenarios "Attended rows expose their origin" and "Absent rows have no origin" (#673). */
    @Test
    void sessions_render_kind_by_name_for_attended_rows_and_as_an_explicit_null_for_absent_rows() throws Exception {
        UUID studentId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-09-03T22:00:00Z");
        AttendanceHistoryView view = new AttendanceHistoryView(
            "2026-09", 3, 2, 1, new BigDecimal("66.67"), List.of(
                new AttendanceSessionView(
                    UUID.randomUUID(), scheduledAt, "Salsa", "Ana", AttendanceStatus.ATTENDED,
                    scheduledAt.plusSeconds(120), AttendanceKind.QR
                ),
                new AttendanceSessionView(
                    UUID.randomUUID(), scheduledAt.plusSeconds(86400), "Salsa", "Ana",
                    AttendanceStatus.ATTENDED, scheduledAt.plusSeconds(86520), AttendanceKind.MANUAL
                ),
                new AttendanceSessionView(
                    UUID.randomUUID(), scheduledAt.plusSeconds(172800), "Salsa", "Ana",
                    AttendanceStatus.ABSENT, null, null
                )
            )
        );
        when(useCase.history(eq(AttendanceViewer.self(studentId)), eq(YearMonth.of(2026, 9)), eq(true)))
            .thenReturn(view);

        mockMvc.perform(get("/api/v1/physical/attendance/me")
                .param("month", "2026-09")
                .param("includeAbsent", "true")
                .principal(authOf(studentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions[0].kind", is("QR")))
            .andExpect(jsonPath("$.sessions[1].kind", is("MANUAL")))
            .andExpect(jsonPath("$.sessions[2]", hasKey("kind")))
            .andExpect(jsonPath("$.sessions[2].kind", nullValue()));
    }

    @Test
    void malformed_month_returns_400_problem_json() throws Exception {
        mockMvc.perform(get("/api/v1/physical/attendance/me")
                .param("month", "not-a-month")
                .principal(authOf(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }

    /**
     * The endpoint has no studentId parameter at all — a caller cannot make it read another
     * student's data through any parameter, because there is none to carry one. Asserted by
     * confirming the viewer built is always {@code self(callerId)}, never influenced by any
     * request parameter.
     */
    @Test
    void a_student_cannot_obtain_another_students_data_via_any_parameter() throws Exception {
        UUID caller = UUID.randomUUID();
        UUID otherStudent = UUID.randomUUID();
        when(useCase.history(any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(
            new AttendanceHistoryView("2026-09", 0, 0, 0, new BigDecimal("0.00"), List.of())
        );

        mockMvc.perform(get("/api/v1/physical/attendance/me")
                .param("month", "2026-09")
                .param("studentId", otherStudent.toString())
                .principal(authOf(caller)))
            .andExpect(status().isOk());

        verify(useCase).history(AttendanceViewer.self(caller), YearMonth.of(2026, 9), false);
    }
}
