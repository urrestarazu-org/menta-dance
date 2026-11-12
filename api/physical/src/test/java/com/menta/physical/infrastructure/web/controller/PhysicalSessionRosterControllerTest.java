package com.menta.physical.infrastructure.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.menta.physical.application.dto.PhysicalSessionRosterView;
import com.menta.physical.application.dto.RosterAttendanceStatus;
import com.menta.physical.application.port.in.GetPhysicalSessionRosterUseCase;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PhysicalSessionRosterControllerTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PRESENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PENDING_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final String URL = "/api/v1/admin/physical/sessions/" + SESSION_ID + "/roster";

    private GetPhysicalSessionRosterUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(GetPhysicalSessionRosterUseCase.class);
        // Boot writes instants as ISO-8601 strings; the standalone setup needs that spelled out.
        // The mixin is what Boot registers so a ProblemDetail's extra properties (code) are top-level.
        JsonMapper mapper = JsonMapper.builder().findAndAddModules()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalSessionRosterController(useCase))
            .setControllerAdvice(new PhysicalCourseExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();
    }

    private static PhysicalSessionRosterView.SessionHeader header(int studentCount) {
        return new PhysicalSessionRosterView.SessionHeader(
            SESSION_ID, COURSE_ID, "Salsa Intermedio", Instant.parse("2026-10-10T22:00:00Z"),
            SessionStatus.SCHEDULED, 20, studentCount
        );
    }

    @Test
    void returns_the_session_header_and_every_student_in_the_use_case_order() throws Exception {
        when(useCase.get(SESSION_ID.toString())).thenReturn(new PhysicalSessionRosterView(header(2), List.of(
            new PhysicalSessionRosterView.Student(
                PRESENT_ID, "Ana Perez", AssignmentType.MONTHLY, RosterAttendanceStatus.PRESENT,
                Instant.parse("2026-10-10T21:47:12Z"), AttendanceKind.QR
            ),
            new PhysicalSessionRosterView.Student(
                PENDING_ID, "Bruno Diaz", AssignmentType.INDIVIDUAL, RosterAttendanceStatus.PENDING, null, null
            )
        )));

        mockMvc.perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.session.sessionId", is(SESSION_ID.toString())))
            .andExpect(jsonPath("$.session.courseId", is(COURSE_ID.toString())))
            .andExpect(jsonPath("$.session.courseName", is("Salsa Intermedio")))
            .andExpect(jsonPath("$.session.scheduledAt", is("2026-10-10T22:00:00Z")))
            .andExpect(jsonPath("$.session.status", is("SCHEDULED")))
            .andExpect(jsonPath("$.session.capacity", is(20)))
            .andExpect(jsonPath("$.session.studentCount", is(2)))
            .andExpect(jsonPath("$.students", hasSize(2)))
            .andExpect(jsonPath("$.students[0].studentId", is(PRESENT_ID.toString())))
            .andExpect(jsonPath("$.students[0].displayName", is("Ana Perez")))
            .andExpect(jsonPath("$.students[0].assignmentType", is("MONTHLY")))
            .andExpect(jsonPath("$.students[0].attendanceStatus", is("PRESENT")))
            .andExpect(jsonPath("$.students[0].checkedInAt", is("2026-10-10T21:47:12Z")))
            .andExpect(jsonPath("$.students[0].checkInKind", is("QR")))
            .andExpect(jsonPath("$.students[1].studentId", is(PENDING_ID.toString())))
            .andExpect(jsonPath("$.students[1].assignmentType", is("INDIVIDUAL")))
            .andExpect(jsonPath("$.students[1].attendanceStatus", is("PENDING")));
    }

    @Test
    void a_pending_student_has_an_explicit_null_check_in_time_and_variant() throws Exception {
        when(useCase.get(SESSION_ID.toString())).thenReturn(new PhysicalSessionRosterView(header(1), List.of(
            new PhysicalSessionRosterView.Student(
                PENDING_ID, "Bruno Diaz", AssignmentType.INDIVIDUAL, RosterAttendanceStatus.PENDING, null, null
            )
        )));

        mockMvc.perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.students[0].checkedInAt", nullValue()))
            .andExpect(jsonPath("$.students[0].checkInKind", nullValue()))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"checkedInAt\":null")));
    }

    @Test
    void a_session_without_students_has_an_empty_list_not_an_error() throws Exception {
        when(useCase.get(SESSION_ID.toString())).thenReturn(new PhysicalSessionRosterView(header(0), List.of()));

        mockMvc.perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.students", hasSize(0)))
            .andExpect(jsonPath("$.session.studentCount", is(0)));

        verify(useCase).get(SESSION_ID.toString());
    }

    @Test
    void passes_the_path_variable_to_the_use_case_as_it_arrives() throws Exception {
        when(useCase.get("no-es-un-uuid")).thenThrow(new IllegalArgumentException("Invalid SessionId format"));

        mockMvc.perform(get("/api/v1/admin/physical/sessions/no-es-un-uuid/roster"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }

    @Test
    void an_unknown_session_is_a_404_problem() throws Exception {
        when(useCase.get(SESSION_ID.toString())).thenThrow(new SessionNotFoundException());

        mockMvc.perform(get(URL))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("SESSION_NOT_FOUND")));
    }
}
