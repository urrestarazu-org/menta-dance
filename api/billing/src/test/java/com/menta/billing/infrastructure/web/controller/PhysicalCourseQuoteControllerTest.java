package com.menta.billing.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.billing.application.dto.CreatePhysicalCourseQuoteCommand;
import com.menta.billing.application.dto.PhysicalCourseQuoteResult;
import com.menta.billing.application.port.in.CreatePhysicalCourseQuoteUseCase;
import com.menta.billing.domain.exception.IndividualSurchargeTooSmallException;
import com.menta.billing.domain.exception.NoScheduledSessionsException;
import com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException;
import com.menta.billing.domain.exception.PhysicalSessionNotFoundException;
import com.menta.billing.domain.model.PurchaseType;
import com.menta.billing.domain.model.QuoteAvailability;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PhysicalCourseQuoteControllerTest {

    private static final String COURSE_ID = "course-1";

    private CreatePhysicalCourseQuoteUseCase useCase;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        useCase = mock(CreatePhysicalCourseQuoteUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalCourseQuoteController(useCase))
            .setControllerAdvice(new PhysicalCourseQuoteExceptionHandler())
            .build();
    }

    private static PhysicalCourseQuoteResult monthlyResult() {
        return new PhysicalCourseQuoteResult(
            UUID.randomUUID(), COURSE_ID, PurchaseType.MONTHLY, 8, null, new BigDecimal("150.00"), "ARS",
            QuoteAvailability.AVAILABLE, 1, Instant.now(), Instant.now().plusSeconds(3600)
        );
    }

    private static PhysicalCourseQuoteResult individualResult() {
        return new PhysicalCourseQuoteResult(
            UUID.randomUUID(), COURSE_ID, PurchaseType.INDIVIDUAL, 8, "session-1", new BigDecimal("13.75"), "ARS",
            QuoteAvailability.AVAILABLE, 1, Instant.now(), Instant.now().plusSeconds(3600)
        );
    }

    private String bodyOf(String purchaseType, String selectedSessionId) throws Exception {
        return objectMapper.writeValueAsString(new HashMap<String, Object>() {
            {
                put("courseId", COURSE_ID);
                put("purchaseType", purchaseType);
                if (selectedSessionId != null) {
                    put("selectedSessionId", selectedSessionId);
                }
            }
        });
    }

    @Test
    void create_monthly_quote_returns_201() throws Exception {
        when(useCase.create(any(CreatePhysicalCourseQuoteCommand.class))).thenReturn(monthlyResult());

        mockMvc.perform(post("/api/v1/billing/physical/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf("MONTHLY", null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.purchaseType", is("MONTHLY")))
            .andExpect(jsonPath("$.selectedSessionId").doesNotExist())
            .andExpect(jsonPath("$.amount", is(150.00)));
    }

    @Test
    void create_individual_quote_returns_201() throws Exception {
        when(useCase.create(any(CreatePhysicalCourseQuoteCommand.class))).thenReturn(individualResult());

        mockMvc.perform(post("/api/v1/billing/physical/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf("INDIVIDUAL", "session-1")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.purchaseType", is("INDIVIDUAL")))
            .andExpect(jsonPath("$.selectedSessionId", is("session-1")))
            .andExpect(jsonPath("$.amount", is(13.75)));

        verify(useCase).create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.INDIVIDUAL, "session-1"));
    }

    @Test
    void no_pricing_published_returns_404() throws Exception {
        when(useCase.create(any(CreatePhysicalCourseQuoteCommand.class)))
            .thenThrow(new PhysicalCoursePricingNotFoundException());

        mockMvc.perform(post("/api/v1/billing/physical/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf("MONTHLY", null)))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PHYSICAL_COURSE_PRICING_NOT_FOUND")));
    }

    @Test
    void unknown_selected_session_returns_404() throws Exception {
        when(useCase.create(any(CreatePhysicalCourseQuoteCommand.class)))
            .thenThrow(new PhysicalSessionNotFoundException());

        mockMvc.perform(post("/api/v1/billing/physical/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf("INDIVIDUAL", "unknown")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("PHYSICAL_SESSION_NOT_FOUND")));
    }

    @Test
    void no_scheduled_sessions_returns_422() throws Exception {
        when(useCase.create(any(CreatePhysicalCourseQuoteCommand.class)))
            .thenThrow(new NoScheduledSessionsException());

        mockMvc.perform(post("/api/v1/billing/physical/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf("MONTHLY", null)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code", is("NO_SCHEDULED_SESSIONS")));
    }

    @Test
    void surcharge_too_small_returns_422() throws Exception {
        when(useCase.create(any(CreatePhysicalCourseQuoteCommand.class)))
            .thenThrow(new IndividualSurchargeTooSmallException());

        mockMvc.perform(post("/api/v1/billing/physical/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf("INDIVIDUAL", "session-1")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code", is("INDIVIDUAL_SURCHARGE_TOO_SMALL")));
    }

    @Test
    void missing_selected_session_for_individual_returns_422() throws Exception {
        when(useCase.create(any(CreatePhysicalCourseQuoteCommand.class)))
            .thenThrow(new com.menta.billing.domain.exception.SelectedSessionRequiredException());

        mockMvc.perform(post("/api/v1/billing/physical/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf("INDIVIDUAL", null)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code", is("SELECTED_SESSION_REQUIRED")));
    }

    @Test
    void selected_session_sent_for_monthly_returns_422() throws Exception {
        when(useCase.create(any(CreatePhysicalCourseQuoteCommand.class)))
            .thenThrow(new com.menta.billing.domain.exception.SelectedSessionNotAllowedException());

        mockMvc.perform(post("/api/v1/billing/physical/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf("MONTHLY", "session-1")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code", is("SELECTED_SESSION_NOT_ALLOWED")));
    }

    @Test
    void malformed_body_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/billing/physical/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
}
