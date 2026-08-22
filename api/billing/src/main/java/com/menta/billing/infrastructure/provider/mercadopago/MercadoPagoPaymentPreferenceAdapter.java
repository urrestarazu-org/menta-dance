package com.menta.billing.infrastructure.provider.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real adapter for {@link PaymentPreferencePort} — {@code POST
 * /checkout/preferences} against Mercado Pago (US-BILLING-010, ADR-0023).
 *
 * <p>Same shape as {@link MercadoPagoPaymentProviderAdapter}: timeouts on the
 * request factory, a circuit breaker around the call, and no retry of its
 * own. The reason differs though — that one is an idempotent read the inbox
 * may retry freely, this one <em>creates a charge</em>, and US-BILLING-010's
 * integrity NFR forbids opening a second one on an uncertain result. A
 * failure aborts the checkout and rolls its local rows back.</p>
 *
 * <p>{@code external_reference} is the reference Billing generated and already
 * persisted; Mercado Pago echoes it back on the eventual payment, which is
 * what lets the webhook flow correlate a {@code payment.id} that does not
 * exist yet with a local payment that already does.</p>
 */
@Component
public class MercadoPagoPaymentPreferenceAdapter implements PaymentPreferencePort {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final String backUrl;
    private final String notificationUrl;

    public MercadoPagoPaymentPreferenceAdapter(
        @Value("${billing.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
        @Value("${billing.mercadopago.access-token:}") String accessToken,
        @Value("${billing.mercadopago.connect-timeout-ms:2000}") int connectTimeoutMs,
        @Value("${billing.mercadopago.read-timeout-ms:3000}") int readTimeoutMs,
        @Value("${billing.mercadopago.checkout-back-url:}") String backUrl,
        @Value("${billing.mercadopago.notification-url:}") String notificationUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .build();

        this.circuitBreaker = CircuitBreaker.of(
            "mercadoPagoPaymentPreference",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .build()
        );

        this.backUrl = backUrl;
        this.notificationUrl = notificationUrl;
    }

    @Override
    public PaymentPreferenceResult createPreference(PaymentPreferenceRequest request) {
        Supplier<PaymentPreferenceResult> call = () -> {
            MercadoPagoPreferenceResponse response = restClient.post()
                .uri("/checkout/preferences")
                .body(toBody(request))
                .retrieve()
                .body(MercadoPagoPreferenceResponse.class);
            if (response == null || response.id() == null || response.initPoint() == null) {
                throw new IllegalStateException("Mercado Pago returned an incomplete preference body");
            }
            return new PaymentPreferenceResult(response.id(), response.initPoint());
        };
        return circuitBreaker.decorateSupplier(call).get();
    }

    private MercadoPagoPreferenceRequest toBody(PaymentPreferenceRequest request) {
        return new MercadoPagoPreferenceRequest(
            List.of(new MercadoPagoPreferenceItem(
                request.title(), 1, request.amount().getAmount(), request.amount().getCurrency()
            )),
            request.externalReference(),
            toBackUrls(),
            emptyToNull(notificationUrl)
        );
    }

    /**
     * The buyer's return URL is the BFF's callback, never a source of truth:
     * a browser redirect is unsigned and replayable, so it only navigates.
     * Confirmation arrives exclusively through the signed webhook
     * (US-BILLING-002).
     */
    private MercadoPagoBackUrls toBackUrls() {
        String url = emptyToNull(backUrl);
        return url == null ? null : new MercadoPagoBackUrls(url, url, url);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record MercadoPagoPreferenceRequest(
        List<MercadoPagoPreferenceItem> items,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("back_urls") MercadoPagoBackUrls backUrls,
        @JsonProperty("notification_url") String notificationUrl
    ) {
    }

    private record MercadoPagoBackUrls(String success, String failure, String pending) {
    }

    private record MercadoPagoPreferenceItem(
        String title,
        int quantity,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("currency_id") String currencyId
    ) {
    }

    private record MercadoPagoPreferenceResponse(
        String id,
        @JsonProperty("init_point") String initPoint
    ) {
    }
}
