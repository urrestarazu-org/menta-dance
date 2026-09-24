package com.menta.physical.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.in.GetPhysicalAttendanceHistoryUseCase;
import java.math.BigDecimal;
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

/**
 * #39, US-PHYSICAL-002 P2, design C2/C6/C7 — MockMvc slice for the elevated endpoint, mirrors
 * {@code PhysicalAttendanceControllerTest}. {@code AttendanceViewer.elevated(studentId,
 * actingUserId, isAdmin(authentication))} is built from the path variable plus the principal;
 * {@code SecurityConfig} is responsible for the coarse role gate, not this controller.
 */
class PhysicalAttendanceAdminControllerTest {

    private GetPhysicalAttendanceHistoryUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(GetPhysicalAttendanceHistoryUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalAttendanceAdminController(useCase))
            .setControllerAdvice(new PhysicalAttendanceExceptionHandler())
            .build();
    }

    private static Authentication authOf(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    @Test
    void admin_caller_builds_an_admin_viewer_for_the_path_studentid() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        AttendanceHistoryView view =
            new AttendanceHistoryView("2026-09", 5, 4, 1, new BigDecimal("80.00"), List.of());
        when(useCase.history(eq(AttendanceViewer.elevated(studentId, adminId, true)), eq(YearMonth.of(2026, 9)), eq(false)))
            .thenReturn(view);

        mockMvc.perform(get("/api/v1/admin/physical/attendance/{studentId}", studentId)
                .param("month", "2026-09")
                .principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scheduledSessionCount", is(5)))
            .andExpect(jsonPath("$.attended", is(4)))
            .andExpect(jsonPath("$.absent", is(1)))
            .andExpect(jsonPath("$.attendanceRate", is(80.00)));

        verify(useCase).history(AttendanceViewer.elevated(studentId, adminId, true), YearMonth.of(2026, 9), false);
    }

    @Test
    void instructor_caller_builds_a_non_admin_elevated_viewer() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        AttendanceHistoryView view =
            new AttendanceHistoryView("2026-09", 3, 3, 0, new BigDecimal("100.00"), List.of());
        when(useCase.history(
            eq(AttendanceViewer.elevated(studentId, instructorId, false)), eq(YearMonth.of(2026, 9)), eq(true)
        )).thenReturn(view);

        mockMvc.perform(get("/api/v1/admin/physical/attendance/{studentId}", studentId)
                .param("month", "2026-09")
                .param("includeAbsent", "true")
                .principal(authOf(instructorId, "INSTRUCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scheduledSessionCount", is(3)));

        verify(useCase)
            .history(AttendanceViewer.elevated(studentId, instructorId, false), YearMonth.of(2026, 9), true);
    }

    @Test
    void malformed_studentid_returns_400_problem_json() throws Exception {
        mockMvc.perform(get("/api/v1/admin/physical/attendance/{studentId}", "not-a-uuid")
                .param("month", "2026-09")
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }

    @Test
    void malformed_month_returns_400_problem_json() throws Exception {
        mockMvc.perform(get("/api/v1/admin/physical/attendance/{studentId}", UUID.randomUUID())
                .param("month", "not-a-month")
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }
}
