package com.menta.bff.infrastructure.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.usecase.GetPaymentHistoryUseCase;
import com.menta.bff.application.usecase.GetSubscriptionHistoryUseCase;
import com.menta.bff.application.usecase.PaymentHistoryView;
import com.menta.bff.application.usecase.SubscriptionHistoryView;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/** Unit tests of {@link HistoryController} (#363): who may open the page is decided by the security chain. */
class HistoryControllerTest {

    private static final String TOKEN = "access-token-abc";

    private final GetSubscriptionHistoryUseCase subscriptionHistory = mock(GetSubscriptionHistoryUseCase.class);
    private final GetPaymentHistoryUseCase paymentHistory = mock(GetPaymentHistoryUseCase.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new HistoryController(subscriptionHistory, paymentHistory))
            .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
            .build();

    @Test
    @DisplayName("both sections are read with the request's access token and the page is never cached")
    void bothSectionsWithTheToken() throws Exception {
        SubscriptionHistoryView subscriptions = new SubscriptionHistoryView(List.of(), false);
        PaymentHistoryView payments = new PaymentHistoryView(List.of(), false);
        when(subscriptionHistory.execute(TOKEN)).thenReturn(subscriptions);
        when(paymentHistory.execute(TOKEN)).thenReturn(payments);

        mockMvc.perform(get("/dashboard/history").requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(model().attribute("subscriptionHistory", subscriptions))
                .andExpect(model().attribute("paymentHistory", payments));
        verify(subscriptionHistory).execute(TOKEN);
        verify(paymentHistory).execute(TOKEN);
    }

    @Test
    @DisplayName("a request without a token still renders: each use case fails closed on its own")
    void withoutTokenEachUseCaseFailsClosed() throws Exception {
        SubscriptionHistoryView subscriptions = new SubscriptionHistoryView(List.of(), true);
        PaymentHistoryView payments = new PaymentHistoryView(List.of(), true);
        when(subscriptionHistory.execute(null)).thenReturn(subscriptions);
        when(paymentHistory.execute(null)).thenReturn(payments);

        mockMvc.perform(get("/dashboard/history"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("subscriptionHistory", subscriptions))
                .andExpect(model().attribute("paymentHistory", payments));
    }
}
