package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.WebhookSignal;
import com.menta.billing.application.port.in.ReceiveWebhookUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mercado Pago webhook receiver (US-BILLING-002). Does the minimum
 * indispensable — verify, dedupe, persist as {@code RECEIVED} — and nothing
 * else: verification against the provider and every payment effect run in
 * {@code WebhookVerificationWorker}, off this request thread (Mercado Pago
 * has its own timeouts/retries if this handler is slow).
 *
 * <p>{@code data.id} arrives as a query parameter, not in the JSON body —
 * this is Mercado Pago's actual webhook shape (a {@code POST} with an empty
 * or minimal body and the real payload identifier in the query string).</p>
 */
@RestController
@RequestMapping("/api/v1/billing/payments/mercadopago")
@PublicBillingEndpoint
public class PaymentWebhookController {

    private final ReceiveWebhookUseCase receiveWebhookUseCase;

    public PaymentWebhookController(ReceiveWebhookUseCase receiveWebhookUseCase) {
        this.receiveWebhookUseCase = receiveWebhookUseCase;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
        @RequestParam(name = "data.id") String dataId,
        @RequestHeader("x-request-id") String requestId,
        @RequestHeader("x-signature") String signatureHeader
    ) {
        receiveWebhookUseCase.receive(new WebhookSignal(dataId, requestId, signatureHeader));
        return ResponseEntity.ok().build();
    }
}
