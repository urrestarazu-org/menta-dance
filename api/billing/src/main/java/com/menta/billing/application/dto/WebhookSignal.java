package com.menta.billing.application.dto;

/** Raw webhook input the controller hands to {@code ReceiveWebhookUseCase}, untrusted until verified. */
public record WebhookSignal(String dataId, String requestId, String signatureHeader) {
}
