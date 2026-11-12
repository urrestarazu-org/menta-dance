package com.menta.physical.infrastructure.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import com.menta.physical.application.dto.PhysicalDaySessionView;
import com.menta.physical.application.port.in.ListPhysicalSessionsByDateUseCase;
import com.menta.physical.domain.model.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PhysicalDaySessionsControllerTest {

    private static final String URL = "/api/v1/admin/physical/sessions";
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private ListPhysicalSessionsByDateUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(ListPhysicalSessionsByDateUseCase.class);
        // Boot writes instants as ISO-8601 strings; the standalone setup needs that spelled out.
        // The mixin is what Boot registers so a ProblemDetail's extra properties (code) are top-level.
        JsonMapper mapper = JsonMapper.builder().findAndAddModules()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalDaySessionsController(useCase))
            .setControllerAdvice(new PhysicalCourseExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();
    }

    @Test
    void returns_every_session_of_the_requested_day_in_the_use_case_order() throws Exception {
        when(useCase.list(LocalDate.of(2026, 10, 10))).thenReturn(List.of(
            new PhysicalDaySessionView(
                SESSION_ID, COURSE_ID, "Salsa Intermedio", Instant.parse("2026-10-10T22:00:00Z"),
                Instant.parse("2026-10-10T23:30:00Z"), SessionStatus.SCHEDULED, 20, 8
            ),
            new PhysicalDaySessionView(
                UUID.randomUUID(), UUID.randomUUID(), "Tango", Instant.parse("2026-10-11T01:00:00Z"),
                Instant.parse("2026-10-11T02:00:00Z"), SessionStatus.CANCELLED, 10, 0
            )
        ));

        mockMvc.perform(get(URL).param("date", "2026-10-10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions", hasSize(2)))
            .andExpect(jsonPath("$.sessions[0].sessionId", is(SESSION_ID.toString())))
            .andExpect(jsonPath("$.sessions[0].courseId", is(COURSE_ID.toString())))
            .andExpect(jsonPath("$.sessions[0].courseName", is("Salsa Intermedio")))
            .andExpect(jsonPath("$.sessions[0].scheduledAt", is("2026-10-10T22:00:00Z")))
            .andExpect(jsonPath("$.sessions[0].endsAt", is("2026-10-10T23:30:00Z")))
            .andExpect(jsonPath("$.sessions[0].status", is("SCHEDULED")))
            .andExpect(jsonPath("$.sessions[0].capacity", is(20)))
            .andExpect(jsonPath("$.sessions[0].occupied", is(8)))
            .andExpect(jsonPath("$.sessions[1].courseName", is("Tango")))
            .andExpect(jsonPath("$.sessions[1].status", is("CANCELLED")));
    }

    @Test
    void a_day_without_sessions_is_an_empty_list_not_an_error() throws Exception {
        when(useCase.list(LocalDate.of(2026, 10, 11))).thenReturn(List.of());

        mockMvc.perform(get(URL).param("date", "2026-10-11"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions", hasSize(0)));

        verify(useCase).list(LocalDate.of(2026, 10, 11));
    }

    @Test
    void a_missing_date_is_a_400_problem_without_reaching_the_use_case() throws Exception {
        mockMvc.perform(get(URL))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(useCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", " ", "10/10/2026", "2026-10", "2026-13-01", "2026-02-30", "20261010", "2026-10-10+01:00", "hoy"
    })
    void a_malformed_date_is_a_400_problem_without_reaching_the_use_case(String date) throws Exception {
        mockMvc.perform(get(URL).param("date", date))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(useCase);
    }
}
