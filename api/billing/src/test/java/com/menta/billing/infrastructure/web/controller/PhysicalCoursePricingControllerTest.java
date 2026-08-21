package com.menta.billing.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import com.menta.billing.application.dto.UpdatePhysicalCoursePricingCommand;
import com.menta.billing.application.port.in.GetPhysicalCoursePricingUseCase;
import com.menta.billing.application.port.in.UpdatePhysicalCoursePricingUseCase;
import com.menta.billing.domain.exception.InvalidIndividualSurchargeException;
import com.menta.billing.domain.exception.PhysicalCourseNotFoundException;
import com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException;
import com.menta.billing.domain.exception.PricingNotOwnedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PhysicalCoursePricingControllerTest {

    private static final String COURSE_ID = "course-1";

    private GetPhysicalCoursePricingUseCase getUseCase;
    private UpdatePhysicalCoursePricingUseCase updateUseCase;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        getUseCase = mock(GetPhysicalCoursePricingUseCase.class);
        updateUseCase = mock(UpdatePhysicalCoursePricingUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalCoursePricingController(getUseCase, updateUseCase))
            .setControllerAdvice(new PhysicalCoursePricingExceptionHandler())
            .build();
    }

    private static Authentication authOf(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private static PhysicalCoursePricingResult result(int version) {
        return new PhysicalCoursePricingResult(
            COURSE_ID, new BigDecimal("150.00"), "ARS", new BigDecimal("10.00"), version, Instant.now()
        );
    }

    @Test
    void get_returns_the_current_pricing() throws Exception {
        when(getUseCase.getPricing(COURSE_ID)).thenReturn(result(2));

        mockMvc.perform(get("/api/v1/billing/physical/courses/{courseId}/pricing", COURSE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courseId", is(COURSE_ID)))
            .andExpect(jsonPath("$.version", is(2)));
    }

    @Test
    void get_no_pricing_published_returns_404() throws Exception {
        when(getUseCase.getPricing(COURSE_ID)).thenThrow(new PhysicalCoursePricingNotFoundException());

        mockMvc.perform(get("/api/v1/billing/physical/courses/{courseId}/pricing", COURSE_ID))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PHYSICAL_COURSE_PRICING_NOT_FOUND")));
    }

    @Test
    void update_publishes_a_new_version() throws Exception {
        UUID professorId = UUID.randomUUID();
        when(updateUseCase.update(
            eq(COURSE_ID), any(UpdatePhysicalCoursePricingCommand.class), eq(professorId), eq(false)
        )).thenReturn(result(2));

        String body = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {
            {
                put("monthlyPrice", new BigDecimal("150.00"));
                put("individualSurchargePercent", new BigDecimal("10.00"));
                put("reason", "ajuste de temporada");
            }
        });

        mockMvc.perform(put("/api/v1/billing/physical/courses/{courseId}/pricing", COURSE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .principal(authOf(professorId, "INSTRUCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version", is(2)));
        verify(updateUseCase).update(
            eq(COURSE_ID), any(UpdatePhysicalCoursePricingCommand.class), eq(professorId), eq(false)
        );
    }

    @Test
    void update_course_not_found_returns_404() throws Exception {
        UUID actingUserId = UUID.randomUUID();
        when(updateUseCase.update(
            eq(COURSE_ID), any(UpdatePhysicalCoursePricingCommand.class), eq(actingUserId), eq(false)
        )).thenThrow(new PhysicalCourseNotFoundException());

        String body = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {
            {
                put("monthlyPrice", new BigDecimal("150.00"));
                put("individualSurchargePercent", new BigDecimal("10.00"));
                put("reason", "motivo");
            }
        });

        mockMvc.perform(put("/api/v1/billing/physical/courses/{courseId}/pricing", COURSE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .principal(authOf(actingUserId, "INSTRUCTOR")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("PHYSICAL_COURSE_NOT_FOUND")));
    }

    @Test
    void update_not_owned_returns_403() throws Exception {
        UUID actingUserId = UUID.randomUUID();
        when(updateUseCase.update(
            eq(COURSE_ID), any(UpdatePhysicalCoursePricingCommand.class), eq(actingUserId), eq(false)
        )).thenThrow(new PricingNotOwnedException());

        String body = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {
            {
                put("monthlyPrice", new BigDecimal("150.00"));
                put("individualSurchargePercent", new BigDecimal("10.00"));
                put("reason", "motivo");
            }
        });

        mockMvc.perform(put("/api/v1/billing/physical/courses/{courseId}/pricing", COURSE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .principal(authOf(actingUserId, "INSTRUCTOR")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("PRICING_NOT_OWNED")));
    }

    @Test
    void update_invalid_surcharge_returns_422() throws Exception {
        UUID actingUserId = UUID.randomUUID();
        when(updateUseCase.update(
            eq(COURSE_ID), any(UpdatePhysicalCoursePricingCommand.class), eq(actingUserId), eq(true)
        )).thenThrow(new InvalidIndividualSurchargeException());

        String body = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {
            {
                put("monthlyPrice", new BigDecimal("150.00"));
                put("individualSurchargePercent", new BigDecimal("0"));
                put("reason", "motivo");
            }
        });

        mockMvc.perform(put("/api/v1/billing/physical/courses/{courseId}/pricing", COURSE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .principal(authOf(actingUserId, "ADMIN")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code", is("INVALID_INDIVIDUAL_SURCHARGE")));
    }

    @Test
    void update_defaults_currency_to_ars_when_omitted() throws Exception {
        UUID professorId = UUID.randomUUID();
        when(updateUseCase.update(
            eq(COURSE_ID), any(UpdatePhysicalCoursePricingCommand.class), eq(professorId), eq(false)
        )).thenReturn(result(1));

        String body = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {
            {
                put("monthlyPrice", new BigDecimal("150.00"));
                put("individualSurchargePercent", new BigDecimal("10.00"));
                put("reason", "motivo");
            }
        });

        mockMvc.perform(put("/api/v1/billing/physical/courses/{courseId}/pricing", COURSE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .principal(authOf(professorId, "INSTRUCTOR")))
            .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<UpdatePhysicalCoursePricingCommand> captor =
            org.mockito.ArgumentCaptor.forClass(UpdatePhysicalCoursePricingCommand.class);
        verify(updateUseCase).update(eq(COURSE_ID), captor.capture(), eq(professorId), eq(false));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().currency()).isEqualTo("ARS");
    }
}
