package com.menta.billing.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.application.dto.PlanCourseResult;
import com.menta.billing.application.dto.PlanDetailResult;
import com.menta.billing.application.dto.PlanSummaryResult;
import com.menta.billing.application.port.in.GetPlanUseCase;
import com.menta.billing.application.port.in.ListPlansUseCase;
import com.menta.billing.domain.exception.BillingDegradedException;
import com.menta.billing.domain.exception.PlanNotFoundException;
import com.menta.billing.domain.exception.PlanRateLimitedException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlanControllerTest {

    private MockMvc mockMvc;
    private ListPlansUseCase listPlansUseCase;
    private GetPlanUseCase getPlanUseCase;

    @BeforeEach
    void setUp() {
        listPlansUseCase = mock(ListPlansUseCase.class);
        getPlanUseCase = mock(GetPlanUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PlanController(listPlansUseCase, getPlanUseCase, new ClientFingerprint()))
            .setControllerAdvice(new PlanExceptionHandler())
            .build();
    }

    private static PlanSummaryResult summary(String id, boolean featured) {
        return new PlanSummaryResult(
            id, "Plan Mensual", "desc", new BigDecimal("15000.00"), "ARS", 30, featured,
            List.of(new PlanCourseResult("course-1", "Tango Basico"))
        );
    }

    @Test
    void list_returns_the_plans_wrapped_under_a_plans_key() throws Exception {
        when(listPlansUseCase.listActivePlans(any())).thenReturn(List.of(summary("plan-1", true)));

        mockMvc.perform(get("/api/v1/billing/plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plans[0].id", is("plan-1")))
            .andExpect(jsonPath("$.plans[0].featured", is(true)))
            .andExpect(jsonPath("$.plans[0].courses[0].name", is("Tango Basico")));
    }

    @Test
    void list_returns_200_with_an_empty_array_when_there_are_no_active_plans() throws Exception {
        when(listPlansUseCase.listActivePlans(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/billing/plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plans").isArray())
            .andExpect(jsonPath("$.plans").isEmpty());
    }

    @Test
    void list_rate_limited_returns_429_with_retry_after() throws Exception {
        when(listPlansUseCase.listActivePlans(any()))
            .thenThrow(new PlanRateLimitedException(Duration.ofSeconds(15)));

        mockMvc.perform(get("/api/v1/billing/plans"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", is("15")))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PLAN_RATE_LIMITED")));
    }

    @Test
    void list_degraded_returns_503_with_retry_after() throws Exception {
        when(listPlansUseCase.listActivePlans(any()))
            .thenThrow(new BillingDegradedException(new RuntimeException("connection refused")));

        mockMvc.perform(get("/api/v1/billing/plans"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", is("30")))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("BILLING_DEGRADED")));
    }

    @Test
    void get_returns_the_full_detail() throws Exception {
        PlanDetailResult detail = new PlanDetailResult(
            "plan-1", "Plan Mensual", "desc", new BigDecimal("15000.00"), "ARS", 30, true,
            "terms", "cancellation", List.of()
        );
        when(getPlanUseCase.getPlan(org.mockito.ArgumentMatchers.eq("plan-1"), any())).thenReturn(detail);

        mockMvc.perform(get("/api/v1/billing/plans/plan-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.termsAndConditions", is("terms")))
            .andExpect(jsonPath("$.cancellationPolicy", is("cancellation")));
    }

    @Test
    void get_unknown_or_inactive_plan_returns_a_generic_404_rfc9457_problem() throws Exception {
        when(getPlanUseCase.getPlan(org.mockito.ArgumentMatchers.eq("missing"), any()))
            .thenThrow(new PlanNotFoundException());

        mockMvc.perform(get("/api/v1/billing/plans/missing"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PLAN_NOT_FOUND")))
            .andExpect(jsonPath("$.detail", is("Plan no encontrado o no disponible.")));
    }
}
