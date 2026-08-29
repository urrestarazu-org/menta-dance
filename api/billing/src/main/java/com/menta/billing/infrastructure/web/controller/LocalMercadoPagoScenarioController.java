package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.infrastructure.provider.local.LocalWebhookPreparationService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** E2E-only endpoint that prepares a signed notification; it never receives or processes the webhook. */
@RestController
@Profile("e2e-mercadopago")
@RequestMapping("/api/v1/e2e/mercadopago")
@PublicBillingEndpoint
public final class LocalMercadoPagoScenarioController {

    private final LocalWebhookPreparationService preparationService;

    public LocalMercadoPagoScenarioController(LocalWebhookPreparationService preparationService) {
        this.preparationService = preparationService;
    }

    @PostMapping("/approved-webhook")
    public WebhookResponse prepareApprovedWebhook(@RequestBody ApprovedWebhookRequest request) {
        LocalWebhookPreparationService.LocalWebhookNotification notification = preparationService.prepareApproved(
            request.externalReference(), request.providerPaymentId(), request.requestId()
        );
        return new WebhookResponse(
            notification.providerPaymentId(), notification.requestId(), notification.signature()
        );
    }

    public record ApprovedWebhookRequest(String externalReference, String providerPaymentId, String requestId) { }
    public record WebhookResponse(String providerPaymentId, String requestId, String signature) { }
}
