package com.menta.physical.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.in.GetPhysicalAttendanceHistoryUseCase;
import com.menta.physical.domain.exception.InvalidSessionRangeException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
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

    private static final String URL = "/api/v1/admin/physical/attendance/{studentId}";
    // The local month of September 2026 in Buenos Aires, as the client computes it.
    private static final String FROM = "2026-09-01T03:00:00Z";
    private static final String TO = "2026-10-01T03:00:00Z";
    private static final Instant FROM_INSTANT = Instant.parse(FROM);
    private static final Instant TO_INSTANT = Instant.parse(TO);

    private GetPhysicalAttendanceHistoryUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(GetPhysicalAttendanceHistoryUseCase.class);
        // Boot writes instants as ISO-8601 strings; the standalone setup needs that spelled out.
        // The mixin is what Boot registers so a ProblemDetail's extra properties (code) are top-level.
        JsonMapper mapper = JsonMapper.builder().findAndAddModules()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalAttendanceAdminController(useCase))
            .setControllerAdvice(new PhysicalAttendanceExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
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
            new AttendanceHistoryView(FROM_INSTANT, TO_INSTANT, 5, 4, 1, new BigDecimal("80.00"), List.of());
        when(useCase.history(
            eq(AttendanceViewer.elevated(studentId, adminId, true)), eq(FROM_INSTANT), eq(TO_INSTANT), eq(false)))
            .thenReturn(view);

        mockMvc.perform(get(URL, studentId)
                .param("from", FROM).param("to", TO)
                .principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.from", is("2026-09-01T03:00:00Z")))
            .andExpect(jsonPath("$.to", is("2026-10-01T03:00:00Z")))
            .andExpect(jsonPath("$.scheduledSessionCount", is(5)))
            .andExpect(jsonPath("$.attended", is(4)))
            .andExpect(jsonPath("$.absent", is(1)))
            .andExpect(jsonPath("$.attendanceRate", is(80.00)));

        verify(useCase)
            .history(AttendanceViewer.elevated(studentId, adminId, true), FROM_INSTANT, TO_INSTANT, false);
    }

    @Test
    void instructor_caller_builds_a_non_admin_elevated_viewer() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        AttendanceHistoryView view =
            new AttendanceHistoryView(FROM_INSTANT, TO_INSTANT, 3, 3, 0, new BigDecimal("100.00"), List.of());
        when(useCase.history(
            eq(AttendanceViewer.elevated(studentId, instructorId, false)), eq(FROM_INSTANT), eq(TO_INSTANT), eq(true)
        )).thenReturn(view);

        mockMvc.perform(get(URL, studentId)
                .param("from", FROM).param("to", TO)
                .param("includeAbsent", "true")
                .principal(authOf(instructorId, "INSTRUCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scheduledSessionCount", is(3)));

        verify(useCase)
            .history(AttendanceViewer.elevated(studentId, instructorId, false), FROM_INSTANT, TO_INSTANT, true);
    }

    @Test
    void malformed_studentid_returns_400_problem_json() throws Exception {
        mockMvc.perform(get(URL, "not-a-uuid")
                .param("from", FROM).param("to", TO)
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }

    @Test
    void a_missing_or_malformed_bound_returns_400_problem_json_without_reaching_the_use_case() throws Exception {
        UUID studentId = UUID.randomUUID();
        Authentication admin = authOf(UUID.randomUUID(), "ADMIN");

        mockMvc.perform(get(URL, studentId).param("to", TO).principal(admin))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
        mockMvc.perform(get(URL, studentId).param("from", FROM).principal(admin))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
        mockMvc.perform(get(URL, studentId).param("from", "2026-09").param("to", TO).principal(admin))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
        mockMvc.perform(get(URL, studentId).param("month", "2026-09").principal(admin))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(useCase);
    }

    @Test
    void an_offset_instant_is_normalised_to_the_same_utc_instant() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(useCase.history(any(), any(), any(), anyBoolean())).thenReturn(
            new AttendanceHistoryView(FROM_INSTANT, TO_INSTANT, 0, 0, 0, new BigDecimal("0.00"), List.of())
        );

        mockMvc.perform(get(URL, studentId)
                .param("from", "2026-09-01T00:00:00-03:00").param("to", "2026-10-01T06:00:00+03:00")
                .principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isOk());

        verify(useCase)
            .history(AttendanceViewer.elevated(studentId, adminId, true), FROM_INSTANT, TO_INSTANT, false);
    }

    @Test
    void a_range_the_use_case_rejects_is_a_400_problem_naming_the_maximum() throws Exception {
        when(useCase.history(any(), any(), any(), anyBoolean())).thenThrow(new InvalidSessionRangeException(92));

        mockMvc.perform(get(URL, UUID.randomUUID()).param("from", TO).param("to", FROM)
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(jsonPath("$.detail", is("The range must have from before to and span at most 92 days.")));
    }
}
