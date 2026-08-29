package com.menta.billing.infrastructure.provider.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.ParsedSignature;
import com.menta.billing.infrastructure.webhook.HmacSha256WebhookSignatureVerifier;
import com.menta.billing.domain.model.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LocalWebhookPreparationServiceTest {

    private static final String SECRET = "local-webhook-secret";

    @Test
    void prepares_a_provider_result_and_signature_accepted_by_the_real_verifier() {
        LocalMercadoPagoPaymentStore store = new LocalMercadoPagoPaymentStore();
        store.createPreference(new PaymentPreferenceRequest(
            "reference-1", "Monthly plan", Money.of(new BigDecimal("1000.00"), "ARS")
        ));
        LocalWebhookPreparationService service = new LocalWebhookPreparationService(store, SECRET, "merchant-1");

        LocalWebhookPreparationService.LocalWebhookNotification notification =
            service.prepareApproved("reference-1", "provider-1", "request-1");
        ParsedSignature signature = new HmacSha256WebhookSignatureVerifier().parse(notification.signature());

        assertThat(new HmacSha256WebhookSignatureVerifier().isValid(
            notification.providerPaymentId(), notification.requestId(), signature.timestamp(), signature.hash(), SECRET
        )).isTrue();
        assertThat(store.fetchPayment("provider-1").providerStatus()).isEqualTo("approved");
    }
}
