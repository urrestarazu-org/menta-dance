package com.menta.billing.infrastructure.provider.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.domain.model.Money;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real adapter for {@link PaymentProviderPort} — {@code GET
 * /v1/payments/{id}} against Mercado Pago (ADR-0038, ADR-0023).
 *
 * <p>Timeout comes from {@link SimpleClientHttpRequestFactory} on the
 * request itself; the circuit breaker wraps the call. Neither retries — a
 * failure propagates to {@code WebhookVerificationWorker}, which retains the
 * inbox row for retry with backoff on its own cycle. Stacking Resilience4j
 * retry on top of that would double the backoff logic for the same
 * failure.</p>
 */
@Component
public class MercadoPagoPaymentProviderAdapter implements PaymentProviderPort {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public MercadoPagoPaymentProviderAdapter(
        @Value("${billing.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
        @Value("${billing.mercadopago.access-token:}") String accessToken,
        @Value("${billing.mercadopago.connect-timeout-ms:2000}") int connectTimeoutMs,
        @Value("${billing.mercadopago.read-timeout-ms:3000}") int readTimeoutMs
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
            "mercadoPagoPaymentProvider",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .build()
        );
    }

    @Override
    public ProviderPaymentResult fetchPayment(String providerPaymentId) {
        Supplier<ProviderPaymentResult> call = () -> {
            MercadoPagoPaymentResponse response = restClient.get()
                .uri("/v1/payments/{id}", providerPaymentId)
                .retrieve()
                .body(MercadoPagoPaymentResponse.class);
            if (response == null) {
                throw new IllegalStateException("Mercado Pago returned an empty payment body");
            }
            return new ProviderPaymentResult(
                response.status(),
                Money.of(response.transactionAmount(), response.currencyId()),
                response.externalReference(),
                String.valueOf(response.collectorId())
            );
        };
        return circuitBreaker.decorateSupplier(call).get();
    }

    private record MercadoPagoPaymentResponse(
        String status,
        @JsonProperty("transaction_amount") BigDecimal transactionAmount,
        @JsonProperty("currency_id") String currencyId,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("collector_id") Long collectorId
    ) {
    }
}
