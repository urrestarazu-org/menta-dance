package com.menta.billing.infrastructure.provider.local;

import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.ProviderPaymentResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Prepares a valid local webhook while leaving receipt and processing to Billing's real flow. */
@Component
@Profile("e2e-mercadopago")
public final class LocalWebhookPreparationService {

    private final LocalMercadoPagoPaymentStore store;
    private final String hmacSecret;
    private final String merchantAccountId;

    public LocalWebhookPreparationService(
        LocalMercadoPagoPaymentStore store,
        @Value("${billing.webhook.mercadopago.hmac-secret}") String hmacSecret,
        @Value("${billing.mercadopago.merchant-account-id:local-merchant}") String merchantAccountId
    ) {
        this.store = store;
        this.hmacSecret = hmacSecret;
        this.merchantAccountId = merchantAccountId;
    }

    public LocalWebhookNotification prepareApproved(String externalReference, String providerPaymentId, String requestId) {
        PaymentPreferenceRequest request = store.preferenceRequest(externalReference);
        store.registerPayment(providerPaymentId, new ProviderPaymentResult(
            "approved", request.amount(), externalReference, merchantAccountId
        ));
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        return new LocalWebhookNotification(providerPaymentId, requestId, "ts=" + timestamp + ",v1="
            + hmac(providerPaymentId, requestId, timestamp));
    }

    private String hmac(String dataId, String requestId, String timestamp) {
        try {
            String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare local webhook signature", exception);
        }
    }

    public record LocalWebhookNotification(String providerPaymentId, String requestId, String signature) { }
}
