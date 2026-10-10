package com.menta.physical.infrastructure.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.menta.physical.application.dto.AssignedSessionView;
import com.menta.physical.application.dto.AssignedSessionsView;
import com.menta.physical.application.port.in.ListMyPhysicalSessionsUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PhysicalMySessionsControllerTest {

    private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private ListMyPhysicalSessionsUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(ListMyPhysicalSessionsUseCase.class);
        // Boot writes instants as ISO-8601 strings; the standalone setup needs that spelled out.
        JsonMapper mapper = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalMySessionsController(useCase))
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
        when(useCase.list(STUDENT_ID)).thenReturn(new AssignedSessionsView(
            Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-12-09T03:00:00Z"),
            List.of(new AssignedSessionView(
                SESSION_ID, COURSE_ID, "Salsa Intermedio", "Ana Perez",
                Instant.parse("2026-10-10T22:00:00Z"), true
            ))
        ));

        mockMvc.perform(get("/api/v1/physical/sessions/me").principal(authOf(STUDENT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.from", is("2026-10-10T03:00:00Z")))
            .andExpect(jsonPath("$.to", is("2026-12-09T03:00:00Z")))
            .andExpect(jsonPath("$.sessions", hasSize(1)))
            .andExpect(jsonPath("$.sessions[0].sessionId", is(SESSION_ID.toString())))
            .andExpect(jsonPath("$.sessions[0].courseId", is(COURSE_ID.toString())))
            .andExpect(jsonPath("$.sessions[0].courseName", is("Salsa Intermedio")))
            .andExpect(jsonPath("$.sessions[0].instructorName", is("Ana Perez")))
            .andExpect(jsonPath("$.sessions[0].scheduledAt", is("2026-10-10T22:00:00Z")))
            .andExpect(jsonPath("$.sessions[0].checkInOpen", is(true)));
    }

    /** The subject is always the token's: a {@code studentId} query parameter has nowhere to go. */
    @Test
    void ignores_a_student_id_parameter_and_always_uses_the_caller() throws Exception {
        when(useCase.list(STUDENT_ID)).thenReturn(new AssignedSessionsView(
            Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-12-09T03:00:00Z"), List.of()
        ));

        mockMvc.perform(get("/api/v1/physical/sessions/me")
                .param("studentId", UUID.randomUUID().toString())
                .principal(authOf(STUDENT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions", hasSize(0)));

        verify(useCase).list(STUDENT_ID);
    }
}
