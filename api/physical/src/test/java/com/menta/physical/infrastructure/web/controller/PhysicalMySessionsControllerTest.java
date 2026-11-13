package com.menta.physical.infrastructure.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
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
import com.menta.physical.application.dto.AssignedSessionView;
import com.menta.physical.application.dto.AssignedSessionsView;
import com.menta.physical.application.port.in.ListMyPhysicalSessionsUseCase;
import com.menta.physical.domain.exception.InvalidSessionRangeException;
import com.menta.shared.physical.AssignmentType;
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

class PhysicalMySessionsControllerTest {

    private static final String URL = "/api/v1/physical/sessions/me";
    private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    // The local day 2026-10-10 in Buenos Aires, as the client computes it.
    private static final String FROM = "2026-10-10T03:00:00Z";
    private static final String TO = "2026-10-11T03:00:00Z";
    private static final Instant FROM_INSTANT = Instant.parse(FROM);
    private static final Instant TO_INSTANT = Instant.parse(TO);

    private ListMyPhysicalSessionsUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(ListMyPhysicalSessionsUseCase.class);
        // Boot writes instants as ISO-8601 strings; the standalone setup needs that spelled out.
        // The mixin is what Boot registers so a ProblemDetail's extra properties (code) are top-level.
        JsonMapper mapper = JsonMapper.builder().findAndAddModules()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalMySessionsController(useCase))
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
    void returns_the_range_and_each_session_for_the_authenticated_student() throws Exception {
        when(useCase.list(STUDENT_ID, FROM_INSTANT, TO_INSTANT)).thenReturn(new AssignedSessionsView(
            FROM_INSTANT, TO_INSTANT,
            List.of(new AssignedSessionView(
                SESSION_ID, COURSE_ID, "Salsa Intermedio", "Ana Perez",
                Instant.parse("2026-10-10T22:00:00Z"), true, AssignmentType.INDIVIDUAL
            ))
        ));

        mockMvc.perform(get(URL).param("from", FROM).param("to", TO).principal(authOf(STUDENT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.from", is("2026-10-10T03:00:00Z")))
            .andExpect(jsonPath("$.to", is("2026-10-11T03:00:00Z")))
            .andExpect(jsonPath("$.sessions", hasSize(1)))
            .andExpect(jsonPath("$.sessions[0].sessionId", is(SESSION_ID.toString())))
            .andExpect(jsonPath("$.sessions[0].courseId", is(COURSE_ID.toString())))
            .andExpect(jsonPath("$.sessions[0].courseName", is("Salsa Intermedio")))
            .andExpect(jsonPath("$.sessions[0].instructorName", is("Ana Perez")))
            .andExpect(jsonPath("$.sessions[0].scheduledAt", is("2026-10-10T22:00:00Z")))
            .andExpect(jsonPath("$.sessions[0].checkInOpen", is(true)))
            .andExpect(jsonPath("$.sessions[0].assignmentType", is("INDIVIDUAL")));
    }

    /** The subject is always the token's: a {@code studentId} query parameter has nowhere to go. */
    @Test
    void ignores_a_student_id_parameter_and_always_uses_the_caller() throws Exception {
        when(useCase.list(STUDENT_ID, FROM_INSTANT, TO_INSTANT))
            .thenReturn(new AssignedSessionsView(FROM_INSTANT, TO_INSTANT, List.of()));

        mockMvc.perform(get(URL)
                .param("from", FROM).param("to", TO)
                .param("studentId", UUID.randomUUID().toString())
                .principal(authOf(STUDENT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions", hasSize(0)));

        verify(useCase).list(STUDENT_ID, FROM_INSTANT, TO_INSTANT);
    }

    /** {@code %2B} is how a client sends the {@code +} of an offset: the container decodes it. */
    @Test
    void an_offset_instant_is_normalised_to_the_same_utc_instant() throws Exception {
        when(useCase.list(STUDENT_ID, FROM_INSTANT, TO_INSTANT))
            .thenReturn(new AssignedSessionsView(FROM_INSTANT, TO_INSTANT, List.of()));

        mockMvc.perform(get(URL)
                .param("from", "2026-10-10T00:00:00-03:00").param("to", "2026-10-11T06:00:00+03:00")
                .principal(authOf(STUDENT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.from", is("2026-10-10T03:00:00Z")))
            .andExpect(jsonPath("$.to", is("2026-10-11T03:00:00Z")));

        verify(useCase).list(STUDENT_ID, FROM_INSTANT, TO_INSTANT);
    }

    @Test
    void a_missing_from_is_a_400_problem_without_reaching_the_use_case() throws Exception {
        mockMvc.perform(get(URL).param("to", TO).principal(authOf(STUDENT_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(useCase);
    }

    @Test
    void a_missing_to_is_a_400_problem_without_reaching_the_use_case() throws Exception {
        mockMvc.perform(get(URL).param("from", FROM).principal(authOf(STUDENT_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(useCase);
    }

    @Test
    void no_parameters_at_all_is_a_400_problem_without_reaching_the_use_case() throws Exception {
        mockMvc.perform(get(URL).principal(authOf(STUDENT_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(useCase);
    }

    /** An unencoded {@code +} reaches the application as a space: it is not an instant. */
    @ParameterizedTest
    @ValueSource(strings = {
        "", " ", "2026-10-10", "2026-10-10T03:00:00", "2026-10-10T03:00Z", "2026-10-10 03:00:00Z",
        "2026-10-10T00:00:00 03:00", "2026-13-10T03:00:00Z", "2026-02-30T03:00:00Z", "1760065200", "hoy"
    })
    void a_malformed_bound_is_a_400_problem_without_reaching_the_use_case(String malformed) throws Exception {
        mockMvc.perform(get(URL).param("from", malformed).param("to", TO).principal(authOf(STUDENT_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
        mockMvc.perform(get(URL).param("from", FROM).param("to", malformed).principal(authOf(STUDENT_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(useCase);
    }

    @Test
    void a_range_the_use_case_rejects_is_a_400_problem_naming_the_maximum() throws Exception {
        when(useCase.list(any(), any(), any())).thenThrow(new InvalidSessionRangeException(92));

        mockMvc.perform(get(URL).param("from", TO).param("to", FROM).principal(authOf(STUDENT_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(jsonPath("$.detail", is("The range must have from before to and span at most 92 days.")));
    }
}
