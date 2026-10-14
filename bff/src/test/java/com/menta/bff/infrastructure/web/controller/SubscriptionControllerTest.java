package com.menta.bff.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.dto.SubscriptionCheckout;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import com.menta.bff.application.usecase.SubscribeToPlanUseCase;
import com.menta.bff.infrastructure.config.ClassesProperties;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

/** Standalone MockMvc tests for {@code POST /suscripciones}. Security is proven by the integration tier. */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionController checkout")
class SubscriptionControllerTest {

    private static final String TOKEN = "access-token-abc";
    private static final String CHECKOUT_URL = "https://pay.example.com/c/1";

    @Mock
    private SubscribeToPlanUseCase subscribeToPlanUseCase;

    private MockMvc mockMvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        View noOpView = (model, request, response) -> {};
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SubscriptionController(subscribeToPlanUseCase, new ClassesProperties()))
                .setSingleView(noOpView)
                .build();
        session = new MockHttpSession();
    }

    private ResultActions submit(String planId, String key) throws Exception {
        return mockMvc.perform(post("/suscripciones").session(session)
                .param("planId", planId).param("idempotencyKey", key)
                .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN));
    }

    @Test
    @DisplayName("an allowed checkout URL redirects and keeps the payment and the kind in the session")
    void redirectsAndStoresSession() throws Exception {
        when(subscribeToPlanUseCase.execute(TOKEN, "plan-1", "key-1"))
                .thenReturn(new SubscriptionCheckout("pay-1", CHECKOUT_URL));

        submit("plan-1", "key-1")
                .andExpect(view().name("redirect:" + CHECKOUT_URL))
                .andExpect(header().string("Cache-Control", "no-store"));

        assertThat(session.getAttribute(PhysicalPurchaseController.PAYMENT_ID_SESSION_ATTRIBUTE))
                .isEqualTo("pay-1");
        assertThat(session.getAttribute(PhysicalPurchaseController.PURCHASE_KIND_SESSION_ATTRIBUTE))
                .isEqualTo("SUBSCRIPTION");
    }

    @Test
    @DisplayName("a checkout URL the policy rejects shows a notice and stores nothing")
    void rejectedUrl() throws Exception {
        when(subscribeToPlanUseCase.execute(TOKEN, "plan-1", "key-1"))
                .thenReturn(new SubscriptionCheckout("pay-1", "http://evil.example.com/c/1"));

        submit("plan-1", "key-1")
                .andExpect(status().isServiceUnavailable())
                .andExpect(view().name("purchase-notice"))
                .andExpect(model().attribute("linkHref", "/plans"));

        assertThat(session.getAttribute(PhysicalPurchaseController.PAYMENT_ID_SESSION_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute(PhysicalPurchaseController.PURCHASE_KIND_SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("409 already active names the end date in the display zone")
    void alreadyActiveWithDate() throws Exception {
        when(subscribeToPlanUseCase.execute(TOKEN, "plan-1", "key-1")).thenThrow(
                new SubscriptionApiClient.SubscriptionAlreadyActiveException(
                        "active", Instant.parse("2026-10-31T15:00:00Z")));

        submit("plan-1", "key-1")
                .andExpect(status().isConflict())
                .andExpect(view().name("purchase-notice"))
                .andExpect(model().attribute("message", "Ya tenés una suscripción vigente hasta el 31/10/2026."))
                .andExpect(model().attribute("linkHref", "/plans"));
    }

    @Test
    @DisplayName("409 already active without a date has no date in the copy")
    void alreadyActiveWithoutDate() throws Exception {
        when(subscribeToPlanUseCase.execute(TOKEN, "plan-1", "key-1"))
                .thenThrow(new SubscriptionApiClient.SubscriptionAlreadyActiveException("active", null));

        submit("plan-1", "key-1")
                .andExpect(status().isConflict())
                .andExpect(model().attribute("message", "Ya tenés una suscripción vigente."));
    }

    @Test
    @DisplayName("409 conflict has its own copy, distinct from already active")
    void conflict() throws Exception {
        when(subscribeToPlanUseCase.execute(TOKEN, "plan-1", "key-1"))
                .thenThrow(new SubscriptionApiClient.SubscriptionConflictException("conflict"));

        submit("plan-1", "key-1")
                .andExpect(status().isConflict())
                .andExpect(model().attribute("message",
                        "La suscripción cambió mientras la procesábamos. Volvé a Planes e intentá de nuevo."));
    }

    @Test
    @DisplayName("422 says the plan is not available")
    void planNotAvailable() throws Exception {
        when(subscribeToPlanUseCase.execute(TOKEN, "plan-1", "key-1"))
                .thenThrow(new SubscriptionApiClient.PlanNotAvailableException("gone"));

        submit("plan-1", "key-1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(model().attribute("message",
                        "Este plan no está disponible para suscribirse en este momento."));
    }

    @Test
    @DisplayName("400 and 503 reuse the shared sentences and never leak upstream details")
    void invalidAndUnavailable() throws Exception {
        when(subscribeToPlanUseCase.execute(TOKEN, "plan-1", "key-1"))
                .thenThrow(new SubscriptionApiClient.InvalidRequestException("secret-detail"));
        submit("plan-1", "key-1").andExpect(status().isBadRequest())
                .andExpect(model().attribute("message", AuthFlowErrors.INVALID_REQUEST));

        when(subscribeToPlanUseCase.execute(TOKEN, "plan-2", "key-2"))
                .thenThrow(new BillingApiClient.ServiceUnavailableException("secret-detail"));
        submit("plan-2", "key-2").andExpect(status().isServiceUnavailable())
                .andExpect(model().attribute("message", AuthFlowErrors.UNAVAILABLE));
    }

    @Test
    @DisplayName("a blank plan id is rejected before any upstream call")
    void blankPlan() throws Exception {
        submit(" ", "key-1")
                .andExpect(status().isBadRequest())
                .andExpect(view().name("purchase-notice"));

        verifyNoInteractions(subscribeToPlanUseCase);
    }

    @Test
    @DisplayName("an over-long key is rejected before any upstream call")
    void longKey() throws Exception {
        submit("plan-1", "k".repeat(129)).andExpect(status().isBadRequest());

        verifyNoInteractions(subscribeToPlanUseCase);
    }
}
