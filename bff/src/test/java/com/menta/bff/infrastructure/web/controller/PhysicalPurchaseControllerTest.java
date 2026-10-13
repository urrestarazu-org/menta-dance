package com.menta.bff.infrastructure.web.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.dto.PaymentProgress;
import com.menta.bff.application.usecase.AssignedSessionRow;
import com.menta.bff.application.usecase.GetAssignedSessionsUseCase;
import com.menta.bff.application.usecase.GetPaymentProgressUseCase;
import com.menta.bff.application.usecase.PayPhysicalPurchaseUseCase;
import com.menta.bff.application.usecase.QuotePhysicalPurchaseUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

/** Standalone MockMvc tests for the return page of {@link PhysicalPurchaseController}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("PhysicalPurchaseController return page")
class PhysicalPurchaseControllerTest {

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

    @BeforeEach
    void setUp() {
        View noOpView = (model, request, response) -> {};
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PhysicalPurchaseController(quotePhysicalPurchaseUseCase,
                        payPhysicalPurchaseUseCase, getPaymentProgressUseCase, getAssignedSessionsUseCase))
                .setSingleView(noOpView)
                .build();
    }

    private static MockHttpServletRequestBuilder returnPage() {
        return get("/compras/retorno")
                .sessionAttr(PhysicalPurchaseController.PAYMENT_ID_SESSION_ATTRIBUTE, "pay-1")
                .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN);
    }

    @Test
    @DisplayName("a confirmed payment lists the assigned sessions")
    void confirmedListsSessions() throws Exception {
        List<AssignedSessionRow> rows = List.of(
                new AssignedSessionRow("s-1", "Salsa en vivo", "lunes 19 de octubre a las 18:00"));
        when(getPaymentProgressUseCase.execute(TOKEN, "pay-1")).thenReturn(PaymentProgress.COMPLETED);
        when(getAssignedSessionsUseCase.execute(TOKEN)).thenReturn(rows);

        mockMvc.perform(returnPage())
                .andExpect(status().isOk())
                .andExpect(view().name("purchase-notice"))
                .andExpect(model().attribute("assignedSessions", rows));
    }

    @ParameterizedTest(name = "{0} does not look up the sessions")
    @EnumSource(value = PaymentProgress.class, names = {"PENDING", "REJECTED", "CANCELLED", "EXPIRED"})
    @DisplayName("a payment that is not confirmed neither queries nor shows sessions")
    void notConfirmedSkipsSessions(PaymentProgress progress) throws Exception {
        when(getPaymentProgressUseCase.execute(TOKEN, "pay-1")).thenReturn(progress);

        mockMvc.perform(returnPage())
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("assignedSessions"));

        verifyNoInteractions(getAssignedSessionsUseCase);
    }

    @Test
    @DisplayName("a confirmed payment whose lookup comes back empty renders the notice without the list")
    void confirmedWithoutSessions() throws Exception {
        when(getPaymentProgressUseCase.execute(TOKEN, "pay-1")).thenReturn(PaymentProgress.COMPLETED);
        when(getAssignedSessionsUseCase.execute(TOKEN)).thenReturn(List.of());

        mockMvc.perform(returnPage())
                .andExpect(status().isOk())
                .andExpect(view().name("purchase-notice"))
                .andExpect(model().attribute("assignedSessions", List.of()));
    }
}
