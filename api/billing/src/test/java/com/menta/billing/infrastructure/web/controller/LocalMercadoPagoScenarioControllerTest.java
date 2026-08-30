package com.menta.billing.infrastructure.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.infrastructure.provider.local.LocalWebhookPreparationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LocalMercadoPagoScenarioControllerTest {

    @Test
    void returns_only_values_needed_to_deliver_the_real_webhook() throws Exception {
        LocalWebhookPreparationService service = org.mockito.Mockito.mock(LocalWebhookPreparationService.class);
        when(service.prepareApproved("reference-1", "provider-1", "request-1")).thenReturn(
            new LocalWebhookPreparationService.LocalWebhookNotification("provider-1", "request-1", "ts=1,v1=abc")
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LocalMercadoPagoScenarioController(service)).build();

        mockMvc.perform(post("/api/v1/e2e/mercadopago/approved-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalReference\":\"reference-1\",\"providerPaymentId\":\"provider-1\",\"requestId\":\"request-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerPaymentId").value("provider-1"))
            .andExpect(jsonPath("$.signature").value("ts=1,v1=abc"))
            .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    void inconsistent_webhook_route_delegates_to_the_preparation_service() throws Exception {
        LocalWebhookPreparationService service = org.mockito.Mockito.mock(LocalWebhookPreparationService.class);
        when(service.prepareInconsistent("reference-2", "provider-2", "request-2")).thenReturn(
            new LocalWebhookPreparationService.LocalWebhookNotification("provider-2", "request-2", "ts=2,v1=def")
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LocalMercadoPagoScenarioController(service)).build();

        mockMvc.perform(post("/api/v1/e2e/mercadopago/inconsistent-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalReference\":\"reference-2\",\"providerPaymentId\":\"provider-2\",\"requestId\":\"request-2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerPaymentId").value("provider-2"))
            .andExpect(jsonPath("$.signature").value("ts=2,v1=def"));
    }
}
