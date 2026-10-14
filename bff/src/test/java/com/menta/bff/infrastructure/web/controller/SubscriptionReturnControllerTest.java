package com.menta.bff.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.dto.PaymentProgress;
import com.menta.bff.application.dto.PhysicalCheckout;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.usecase.GetAssignedSessionsUseCase;
import com.menta.bff.application.usecase.GetPaymentProgressUseCase;
import com.menta.bff.application.usecase.PayPhysicalPurchaseUseCase;
import com.menta.bff.application.usecase.QuotePhysicalPurchaseUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
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

/** The shared return page of {@link PhysicalPurchaseController} when the payment is a subscription. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Return page for a subscription payment")
class SubscriptionReturnControllerTest {

    private static final String TOKEN = "access-token-abc";

    @Mock
    private QuotePhysicalPurchaseUseCase quotePhysicalPurchaseUseCase;

    @Mock
    private PayPhysicalPurchaseUseCase payPhysicalPurchaseUseCase;

    @Mock
    private GetPaymentProgressUseCase getPaymentProgressUseCase;

    @Mock
    private GetAssignedSessionsUseCase getAssignedSessionsUseCase;

    private MockMvc mockMvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        View noOpView = (model, request, response) -> {};
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PhysicalPurchaseController(quotePhysicalPurchaseUseCase,
                        payPhysicalPurchaseUseCase, getPaymentProgressUseCase, getAssignedSessionsUseCase))
                .setSingleView(noOpView)
                .build();
        session = new MockHttpSession();
        session.setAttribute(PhysicalPurchaseController.PAYMENT_ID_SESSION_ATTRIBUTE, "pay-1");
        session.setAttribute(PhysicalPurchaseController.PURCHASE_KIND_SESSION_ATTRIBUTE, "SUBSCRIPTION");
    }

    private ResultActions returnPage() throws Exception {
        return mockMvc.perform(get("/compras/retorno").session(session)
                .param("status", "approved").param("payment_id", "someone-elses")
                .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN));
    }

    @Test
    @DisplayName("a confirmed payment says the subscription is being activated and skips sessions")
    void confirmed() throws Exception {
        when(getPaymentProgressUseCase.execute(TOKEN, "pay-1")).thenReturn(PaymentProgress.COMPLETED);

        returnPage().andExpect(status().isOk())
                .andExpect(view().name("purchase-notice"))
                .andExpect(model().attribute("message", "¡Pago confirmado! Tu suscripción se está activando."))
                .andExpect(model().attribute("linkHref", "/plans"))
                .andExpect(model().attribute("linkLabel", "Ver planes"))
                .andExpect(model().attribute("breadcrumbHref", "/plans"))
                .andExpect(model().attribute("breadcrumbLabel", "Planes"))
                .andExpect(model().attributeDoesNotExist("assignedSessions"));

        verifyNoInteractions(getAssignedSessionsUseCase);
        assertThat(session.getAttribute(PhysicalPurchaseController.PAYMENT_ID_SESSION_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute(PhysicalPurchaseController.PURCHASE_KIND_SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("a pending payment asks to refresh and keeps the session for the next visit")
    void pending() throws Exception {
        when(getPaymentProgressUseCase.execute(TOKEN, "pay-1")).thenReturn(PaymentProgress.PENDING);

        returnPage().andExpect(status().isOk())
                .andExpect(model().attribute("message",
                        "Estamos confirmando tu pago, actualizá en unos minutos."))
                .andExpect(model().attribute("linkHref", "/compras/retorno"))
                .andExpect(model().attribute("linkLabel", "Actualizar"));

        assertThat(session.getAttribute(PhysicalPurchaseController.PURCHASE_KIND_SESSION_ATTRIBUTE))
                .isEqualTo("SUBSCRIPTION");
    }

    @Test
    @DisplayName("a rejected payment says nothing was charged and links to the plans")
    void rejected() throws Exception {
        when(getPaymentProgressUseCase.execute(TOKEN, "pay-1")).thenReturn(PaymentProgress.REJECTED);

        returnPage().andExpect(model().attribute("message", "El pago fue rechazado. No se te cobró nada."))
                .andExpect(model().attribute("linkHref", "/plans"))
                .andExpect(model().attribute("linkLabel", "Ver planes"));
    }

    @Test
    @DisplayName("a cancelled payment links to the plans")
    void cancelled() throws Exception {
        when(getPaymentProgressUseCase.execute(TOKEN, "pay-1")).thenReturn(PaymentProgress.CANCELLED);

        returnPage().andExpect(model().attribute("message", "El pago fue cancelado."))
                .andExpect(model().attribute("linkHref", "/plans"));
    }

    @Test
    @DisplayName("an expired payment links to the plans")
    void expired() throws Exception {
        when(getPaymentProgressUseCase.execute(TOKEN, "pay-1")).thenReturn(PaymentProgress.EXPIRED);

        returnPage().andExpect(model().attribute("message", "El pago venció antes de completarse."))
                .andExpect(model().attribute("linkHref", "/plans"));
    }

    @Test
    @DisplayName("without a stored payment the subscription kind shows the plans fallback, not a redirect")
    void noPaymentInSession() throws Exception {
        session.removeAttribute(PhysicalPurchaseController.PAYMENT_ID_SESSION_ATTRIBUTE);

        returnPage().andExpect(status().isOk())
                .andExpect(view().name("purchase-notice"))
                .andExpect(model().attribute("linkHref", "/plans"))
                .andExpect(model().attribute("linkLabel", "Ver planes"));

        verifyNoInteractions(getPaymentProgressUseCase);
    }

    @Test
    @DisplayName("a 404 for the stored payment shows the plans fallback and clears the session")
    void notFound() throws Exception {
        when(getPaymentProgressUseCase.execute(TOKEN, "pay-1"))
                .thenThrow(new BillingApiClient.NotFoundException("gone"));

        returnPage().andExpect(status().isOk())
                .andExpect(view().name("purchase-notice"))
                .andExpect(model().attribute("linkHref", "/plans"));

        assertThat(session.getAttribute(PhysicalPurchaseController.PAYMENT_ID_SESSION_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute(PhysicalPurchaseController.PURCHASE_KIND_SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("an unavailable API keeps the refresh link on the return page")
    void unavailable() throws Exception {
        when(getPaymentProgressUseCase.execute(TOKEN, "pay-1"))
                .thenThrow(new BillingApiClient.ServiceUnavailableException("down"));

        returnPage().andExpect(status().isServiceUnavailable())
                .andExpect(model().attribute("linkHref", "/compras/retorno"));
    }

    @Test
    @DisplayName("starting a class payment clears the subscription kind left by an earlier checkout")
    void classPayClearsKind() throws Exception {
        when(payPhysicalPurchaseUseCase.execute(TOKEN, "00000000-0000-0000-0000-0000000000b1", "key-1"))
                .thenReturn(new PhysicalCheckout("pay-2", "https://pay.example.com/c/2"));

        mockMvc.perform(post("/compras").session(session)
                        .param("quoteId", "00000000-0000-0000-0000-0000000000b1")
                        .param("idempotencyKey", "key-1")
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(view().name("redirect:https://pay.example.com/c/2"));

        assertThat(session.getAttribute(PhysicalPurchaseController.PAYMENT_ID_SESSION_ATTRIBUTE))
                .isEqualTo("pay-2");
        assertThat(session.getAttribute(PhysicalPurchaseController.PURCHASE_KIND_SESSION_ATTRIBUTE)).isNull();
    }
}
