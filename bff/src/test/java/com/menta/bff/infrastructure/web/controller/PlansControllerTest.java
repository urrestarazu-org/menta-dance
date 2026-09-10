package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.usecase.GetPlansViewUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Unit tests for {@link PlansController}, standalone MockMvc, no security
 * context — anonymous reachability is proven by the integration tier (PR 5),
 * not here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlansController")
class PlansControllerTest {

    @Mock
    private GetPlansViewUseCase getPlansViewUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // The view name "plans" collides with the request path "/plans": the
        // default InternalResourceView forward would loop back to this same
        // handler ("Circular view path"). A no-op View sidesteps rendering
        // entirely — this test only asserts the model attribute and the
        // resolved view name, never actual template output.
        View noOpView = (model, request, response) -> { };
        mockMvc = MockMvcBuilders.standaloneSetup(new PlansController(getPlansViewUseCase))
                .setSingleView(noOpView)
                .build();
    }

    @Test
    @DisplayName("renders the plans view with the model attribute equal to the use case's returned list")
    void shouldRenderPlansViewWithModelAttribute() throws Exception {
        List<PlanSummary> plans = List.of(
                new PlanSummary("plan-1", "Mensual", "Acceso mensual", BigDecimal.valueOf(1000), "ARS", 30, false),
                new PlanSummary("plan-2", "Anual", "Acceso anual", BigDecimal.valueOf(10000), "ARS", 365, true));
        when(getPlansViewUseCase.execute()).thenReturn(plans);

        mockMvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans"))
                .andExpect(model().attribute("plans", plans));
    }

    @Test
    @DisplayName("propagates NotFoundException, which resolves to HTTP 404 via @ResponseStatus")
    void shouldResolveNotFoundExceptionTo404() throws Exception {
        when(getPlansViewUseCase.execute()).thenThrow(new BillingApiClient.NotFoundException("no plans"));

        mockMvc.perform(get("/plans"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("propagates ServiceUnavailableException, which resolves to HTTP 503 via @ResponseStatus")
    void shouldResolveServiceUnavailableExceptionTo503() throws Exception {
        when(getPlansViewUseCase.execute()).thenThrow(new BillingApiClient.ServiceUnavailableException("unavailable"));

        mockMvc.perform(get("/plans"))
                .andExpect(status().isServiceUnavailable());
    }
}
