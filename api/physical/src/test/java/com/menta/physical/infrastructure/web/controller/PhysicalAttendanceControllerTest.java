package com.menta.physical.infrastructure.web.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceSessionView;
import com.menta.physical.application.dto.AttendanceStatus;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.in.GetPhysicalAttendanceHistoryUseCase;
import com.menta.physical.domain.exception.InvalidSessionRangeException;
import com.menta.physical.domain.model.AttendanceKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** #39, US-PHYSICAL-002, design C7 — MockMvc slice, mirrors {@code PhysicalCourseQuoteControllerTest}. */
class PhysicalAttendanceControllerTest {

    private static final String URL = "/api/v1/physical/attendance/me";
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
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalAttendanceController(useCase))
            .setControllerAdvice(new PhysicalAttendanceExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();
    }

    private static Authentication authOf(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    @Test
    void range_happy_path_returns_the_assembled_body_echoing_the_range_for_the_authenticated_student()
        throws Exception {
        UUID studentId = UUID.randomUUID();
        AttendanceHistoryView view =
            new AttendanceHistoryView(FROM_INSTANT, TO_INSTANT, 5, 4, 1, new BigDecimal("80.00"), List.of());
        when(useCase.history(
            eq(AttendanceViewer.self(studentId)), eq(FROM_INSTANT), eq(TO_INSTANT), eq(false)
        )).thenReturn(view);

        mockMvc.perform(get(URL)
                .param("from", FROM).param("to", TO)
                .principal(authOf(studentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.from", is("2026-09-01T03:00:00Z")))
            .andExpect(jsonPath("$.to", is("2026-10-01T03:00:00Z")))
            .andExpect(jsonPath("$.month").doesNotExist())
            .andExpect(jsonPath("$.scheduledSessionCount", is(5)))
            .andExpect(jsonPath("$.attended", is(4)))
            .andExpect(jsonPath("$.absent", is(1)))
            .andExpect(jsonPath("$.attendanceRate", is(80.00)));

        verify(useCase).history(AttendanceViewer.self(studentId), FROM_INSTANT, TO_INSTANT, false);
    }

    /** Scenarios "Attended rows expose their origin" and "Absent rows have no origin" (#673). */
    @Test
    void sessions_render_kind_by_name_for_attended_rows_and_as_an_explicit_null_for_absent_rows() throws Exception {
        UUID studentId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-09-03T22:00:00Z");
        AttendanceHistoryView view = new AttendanceHistoryView(
            FROM_INSTANT, TO_INSTANT, 3, 2, 1, new BigDecimal("66.67"), List.of(
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
        when(useCase.history(eq(AttendanceViewer.self(studentId)), eq(FROM_INSTANT), eq(TO_INSTANT), eq(true)))
            .thenReturn(view);

        mockMvc.perform(get(URL)
                .param("from", FROM).param("to", TO)
                .param("includeAbsent", "true")
                .principal(authOf(studentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions[0].kind", is("QR")))
            .andExpect(jsonPath("$.sessions[1].kind", is("MANUAL")))
            .andExpect(jsonPath("$.sessions[2]", hasKey("kind")))
            .andExpect(jsonPath("$.sessions[2].kind", nullValue()));
    }

    /** {@code %2B} is how a client sends the {@code +} of an offset: the container decodes it. */
    @Test
    void an_offset_instant_is_normalised_to_the_same_utc_instant() throws Exception {
        UUID studentId = UUID.randomUUID();
        when(useCase.history(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(
            new AttendanceHistoryView(FROM_INSTANT, TO_INSTANT, 0, 0, 0, new BigDecimal("0.00"), List.of())
        );

        mockMvc.perform(get(URL)
                .param("from", "2026-08-31T21:00:00-06:00").param("to", "2026-10-01T06:00:00+03:00")
                .principal(authOf(studentId)))
            .andExpect(status().isOk());

        verify(useCase).history(AttendanceViewer.self(studentId), FROM_INSTANT, TO_INSTANT, false);
    }

    @Test
    void a_missing_bound_is_a_400_problem_without_reaching_the_use_case() throws Exception {
        mockMvc.perform(get(URL).param("to", TO).principal(authOf(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
        mockMvc.perform(get(URL).param("from", FROM).principal(authOf(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
        mockMvc.perform(get(URL).principal(authOf(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(useCase);
    }

    /** The old {@code month} parameter is gone: sent alone it is just a missing {@code from}/{@code to}. */
    @Test
    void the_legacy_month_parameter_alone_is_a_400_problem() throws Exception {
        mockMvc.perform(get(URL).param("month", "2026-09").principal(authOf(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(useCase);
    }

    /** An unencoded {@code +} reaches the application as a space: it is not an instant. */
    @ParameterizedTest
    @ValueSource(strings = {
        "", " ", "2026-09", "2026-09-01", "2026-09-01T03:00:00", "2026-09-01 03:00:00Z",
        "2026-09-01T00:00:00 03:00", "2026-13-01T03:00:00Z", "hoy"
    })
    void a_malformed_bound_is_a_400_problem_without_reaching_the_use_case(String malformed) throws Exception {
        mockMvc.perform(get(URL).param("from", malformed).param("to", TO).principal(authOf(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
        mockMvc.perform(get(URL).param("from", FROM).param("to", malformed).principal(authOf(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(useCase);
    }

    @Test
    void a_range_the_use_case_rejects_is_a_400_problem_naming_the_maximum() throws Exception {
        when(useCase.history(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenThrow(new InvalidSessionRangeException(92));

        mockMvc.perform(get(URL).param("from", TO).param("to", FROM).principal(authOf(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(jsonPath("$.detail", is("The range must have from before to and span at most 92 days.")));
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
        when(useCase.history(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(
            new AttendanceHistoryView(FROM_INSTANT, TO_INSTANT, 0, 0, 0, new BigDecimal("0.00"), List.of())
        );

        mockMvc.perform(get(URL)
                .param("from", FROM).param("to", TO)
                .param("studentId", otherStudent.toString())
                .principal(authOf(caller)))
            .andExpect(status().isOk());

        verify(useCase).history(AttendanceViewer.self(caller), FROM_INSTANT, TO_INSTANT, false);
    }
}
