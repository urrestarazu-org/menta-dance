package com.menta.billing.infrastructure.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.application.port.in.ReceiveWebhookUseCase;
import com.menta.billing.domain.exception.WebhookSignatureInvalidException;
import com.menta.billing.domain.exception.WebhookTimestampExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PaymentWebhookControllerTest {

    private MockMvc mockMvc;
    private ReceiveWebhookUseCase receiveWebhookUseCase;

    @BeforeEach
    void setUp() {
        receiveWebhookUseCase = mock(ReceiveWebhookUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentWebhookController(receiveWebhookUseCase))
            .setControllerAdvice(new WebhookExceptionHandler())
            .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhookRequest() {
        return post("/api/v1/billing/payments/mercadopago/webhook")
            .param("data.id", "data-1")
            .header("x-request-id", "req-1")
            .header("x-signature", "ts=1700000000,v1=abcdef");
    }

    @Test
    void returns_200_for_a_valid_webhook() throws Exception {
        mockMvc.perform(webhookRequest()).andExpect(status().isOk());
    }

    @Test
    void returns_401_problem_json_for_an_invalid_signature() throws Exception {
        doThrow(new WebhookSignatureInvalidException()).when(receiveWebhookUseCase).receive(any());

        mockMvc.perform(webhookRequest())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    void returns_401_problem_json_for_an_expired_timestamp() throws Exception {
        doThrow(new WebhookTimestampExpiredException()).when(receiveWebhookUseCase).receive(any());

        mockMvc.perform(webhookRequest())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("WEBHOOK_TIMESTAMP_EXPIRED"));
    }
}
