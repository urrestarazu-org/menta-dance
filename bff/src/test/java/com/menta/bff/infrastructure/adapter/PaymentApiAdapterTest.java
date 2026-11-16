package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.OwnPayment;
import com.menta.bff.application.dto.OwnPayment.ItemType;
import com.menta.bff.application.dto.OwnPayment.Method;
import com.menta.bff.application.dto.OwnPayment.Status;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

/** {@code GET /api/v1/billing/payments/me} through {@link PaymentApiAdapter} (#363). */
@WireMockTest
@DisplayName("PaymentApiAdapter")
class PaymentApiAdapterTest {

    private static final String PAYMENTS = "/api/v1/billing/payments/me";
    private static final String TOKEN = "access-token-abc";

    private PaymentApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        adapter = adapterWith(wireMock, Duration.ofSeconds(1));
    }

    private static PaymentApiAdapter adapterWith(WireMockRuntimeInfo wireMock, Duration timeout) {
        BillingApiProperties properties = new BillingApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(timeout);
        return new PaymentApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stubPayments(int status, String body) {
        stubFor(get(urlPathEqualTo(PAYMENTS)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", status < 300 ? "application/json" : "application/problem+json")
                .withBody(body)));
    }

    private static String payment(String status, String method, String itemType, String itemName) {
        String template =
                """
                {"paymentId": "pay-1", "status": "%s", "amount": 4999.50, "currency": "ARS",
                 "paymentMethod": "%s", "createdAt": "2026-11-02T15:00:00Z", "providerRef": "SECRET",
                 "item": {"type": "%s", "id": "i-1", "name": %s}}
                """;
        return template.formatted(status, method, itemType, itemName);
    }

    @Test
    @DisplayName("maps every payment in API order, sending the Bearer token and the limit")
    void mapsPayments() {
        stubPayments(200, "[" + payment("COMPLETED", "MERCADO_PAGO", "SUBSCRIPTION", "\"Plan Mensual\"") + ","
                + payment("AWAITING_MANUAL_VERIFICATION", "BANK_TRANSFER", "PHYSICAL_COURSE", "null") + "]");

        List<OwnPayment> payments = adapter.listOwnPayments(TOKEN, 20);

        Instant createdAt = Instant.parse("2026-11-02T15:00:00Z");
        assertThat(payments).containsExactly(
                new OwnPayment(Status.COMPLETED, new BigDecimal("4999.50"), "ARS", Method.MERCADO_PAGO, createdAt,
                        ItemType.SUBSCRIPTION, "Plan Mensual"),
                new OwnPayment(Status.AWAITING_MANUAL_VERIFICATION, new BigDecimal("4999.50"), "ARS",
                        Method.BANK_TRANSFER, createdAt, ItemType.PHYSICAL_COURSE, null));
        verify(getRequestedFor(urlPathEqualTo(PAYMENTS))
                .withQueryParam("limit", equalTo("20"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @ParameterizedTest(name = "status {0} maps to itself")
    @ValueSource(strings = {"AWAITING_PROVIDER", "AWAITING_MANUAL_VERIFICATION", "RECONCILIATION_REQUIRED",
        "COMPLETED", "REJECTED", "CANCELLED", "EXPIRED"})
    void everyDocumentedStatus(String status) {
        stubPayments(200, "[" + payment(status, "MERCADO_PAGO", "SUBSCRIPTION", "\"Plan\"") + "]");

        assertThat(adapter.listOwnPayments(TOKEN, 20)).singleElement()
                .satisfies(payment -> assertThat(payment.status()).isEqualTo(Status.valueOf(status)));
    }

    @Test
    @DisplayName("a status, method or item type this BFF does not know maps to UNKNOWN instead of failing")
    void unknownValues() {
        stubPayments(200, "[" + payment("REFUNDED", "CRYPTO", "GIFT_CARD", "null") + "]");

        assertThat(adapter.listOwnPayments(TOKEN, 20)).containsExactly(
                new OwnPayment(Status.UNKNOWN, new BigDecimal("4999.50"), "ARS", Method.UNKNOWN,
                        Instant.parse("2026-11-02T15:00:00Z"), ItemType.UNKNOWN, null));
    }

    @Test
    @DisplayName("an empty array means no payments")
    void empty() {
        stubPayments(200, "[]");

        assertThat(adapter.listOwnPayments(TOKEN, 20)).isEmpty();
    }

    @ParameterizedTest(name = "a {0} answer fails closed")
    @ValueSource(ints = {400, 401, 403, 404, 429, 500, 503})
    void nonSuccessFailsClosed(int status) {
        stubPayments(status, "{\"status\": " + status + ", \"code\": \"SECRET_DETAIL\"}");

        assertThatThrownBy(() -> adapter.listOwnPayments(TOKEN, 20))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("SECRET_DETAIL")
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "an unusable 200 body fails closed: {0}")
    @ValueSource(strings = {
        "not json",
        "",
        "{\"status\": \"COMPLETED\"}",
        "[null]",
        "[{\"amount\":1,\"currency\":\"ARS\",\"paymentMethod\":\"MERCADO_PAGO\",\"createdAt\":\"2026-11-02T15:00:00Z\","
                + "\"item\":{\"type\":\"SUBSCRIPTION\",\"name\":null}}]",
        "[{\"status\":\"COMPLETED\",\"currency\":\"ARS\",\"paymentMethod\":\"MERCADO_PAGO\","
                + "\"createdAt\":\"2026-11-02T15:00:00Z\",\"item\":{\"type\":\"SUBSCRIPTION\",\"name\":null}}]",
        "[{\"status\":\"COMPLETED\",\"amount\":1,\"paymentMethod\":\"MERCADO_PAGO\","
                + "\"createdAt\":\"2026-11-02T15:00:00Z\",\"item\":{\"type\":\"SUBSCRIPTION\",\"name\":null}}]",
        "[{\"status\":\"COMPLETED\",\"amount\":1,\"currency\":\"ARS\","
                + "\"createdAt\":\"2026-11-02T15:00:00Z\",\"item\":{\"type\":\"SUBSCRIPTION\",\"name\":null}}]",
        "[{\"status\":\"COMPLETED\",\"amount\":1,\"currency\":\"ARS\",\"paymentMethod\":\"MERCADO_PAGO\","
                + "\"item\":{\"type\":\"SUBSCRIPTION\",\"name\":null}}]",
        "[{\"status\":\"COMPLETED\",\"amount\":1,\"currency\":\"ARS\",\"paymentMethod\":\"MERCADO_PAGO\","
                + "\"createdAt\":\"2026-11-02T15:00:00Z\"}]",
        "[{\"status\":\"COMPLETED\",\"amount\":1,\"currency\":\"ARS\",\"paymentMethod\":\"MERCADO_PAGO\","
                + "\"createdAt\":\"2026-11-02T15:00:00Z\",\"item\":{\"name\":null}}]",
        "[{\"status\":\"COMPLETED\",\"amount\":\"lots\",\"currency\":\"ARS\",\"paymentMethod\":\"MERCADO_PAGO\","
                + "\"createdAt\":\"2026-11-02T15:00:00Z\",\"item\":{\"type\":\"SUBSCRIPTION\",\"name\":null}}]"
    })
    void unusableBodyFailsClosed(String body) {
        stubPayments(200, body);

        assertThatThrownBy(() -> adapter.listOwnPayments(TOKEN, 20))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a slow answer fails closed once the configured timeout elapses")
    void timeoutFailsClosed(WireMockRuntimeInfo wireMock) {
        PaymentApiAdapter impatient = adapterWith(wireMock, Duration.ofMillis(100));
        stubFor(get(urlPathEqualTo(PAYMENTS)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("[]").withFixedDelay(1000)));

        assertThatThrownBy(() -> impatient.listOwnPayments(TOKEN, 20))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }
}
