package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.ClassCheckout;
import com.menta.bff.application.dto.ClassPaymentMethod;
import com.menta.bff.application.dto.ClassPricing;
import com.menta.bff.application.dto.ClassPurchaseType;
import com.menta.bff.application.dto.ClassQuote;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("PhysicalPurchaseApiAdapter")
class PhysicalPurchaseApiAdapterTest {

    private static final String PRICING_PATH = "/api/v1/billing/physical/courses/course-p/pricing";
    private static final String QUOTES_PATH = "/api/v1/billing/physical/quotes";
    private static final String PURCHASES_PATH = "/api/v1/billing/physical/purchases";
    private static final String TOKEN = "access-token";

    private static final String QUOTE_BODY =
            """
            {"id": "11111111-1111-1111-1111-111111111111", "courseId": "course-p", "purchaseType": "INDIVIDUAL",
             "scheduledSessionCount": 4, "selectedSessionId": "s-1", "amount": 3600.00, "currency": "ARS",
             "availability": "%s", "pricingVersion": 2, "createdAt": "2026-10-04T15:00:00Z",
             "expiresAt": "2026-10-04T16:00:00Z"}
            """;

    private PhysicalPurchaseApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        BillingApiProperties properties = new BillingApiProperties();
        properties.setBaseUrl(wmRuntimeInfo.getHttpBaseUrl());
        properties.setTimeout(Duration.ofSeconds(2));
        adapter = new PhysicalPurchaseApiAdapter(
                WebClient.builder().baseUrl(wmRuntimeInfo.getHttpBaseUrl()).build(), properties);
    }

    private static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }

    // -- pricing --

    @Test
    @DisplayName("maps published pricing, with no Authorization header")
    void findPricing_mapsPricing() {
        stubFor(get(urlEqualTo(PRICING_PATH)).willReturn(json(200,
                """
                {"courseId": "course-p", "monthlyPrice": 12000.00, "currency": "ARS",
                 "individualSurchargePercent": 20.00, "version": 2, "updatedAt": "2026-10-01T00:00:00Z"}
                """)));

        assertThat(adapter.findPricing("course-p")).contains(
                new ClassPricing(new BigDecimal("12000.00"), "ARS", new BigDecimal("20.00")));
        verify(getRequestedFor(urlEqualTo(PRICING_PATH)).withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("a pricing 404 means no price is published yet")
    void findPricing_on404_isEmpty() {
        stubFor(get(urlEqualTo(PRICING_PATH)).willReturn(json(404, "{\"title\":\"not published\"}")));

        assertThat(adapter.findPricing("course-p")).isEmpty();
    }

    @Test
    @DisplayName("a pricing 503 maps to ServiceUnavailableException")
    void findPricing_on503_isUnavailable() {
        stubFor(get(urlEqualTo(PRICING_PATH)).willReturn(json(503, "{}")));

        assertThatThrownBy(() -> adapter.findPricing("course-p"))
                .isInstanceOf(PhysicalPurchaseApiClient.ServiceUnavailableException.class);
    }

    // -- quote --

    @Test
    @DisplayName("creates a quote with the bearer token and maps an AVAILABLE answer")
    void createQuote_sendsTokenAndMapsAvailable() {
        stubFor(post(urlEqualTo(QUOTES_PATH)).willReturn(json(201, QUOTE_BODY.formatted("AVAILABLE"))));

        ClassQuote quote = adapter.createQuote("course-p", ClassPurchaseType.INDIVIDUAL, "s-1", TOKEN);

        assertThat(quote).isEqualTo(new ClassQuote(
                "11111111-1111-1111-1111-111111111111", "course-p", ClassPurchaseType.INDIVIDUAL, "s-1",
                new BigDecimal("3600.00"), "ARS", true, Instant.parse("2026-10-04T16:00:00Z")));
        verify(postRequestedFor(urlEqualTo(QUOTES_PATH))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withRequestBody(equalToJson(
                        """
                        {"courseId": "course-p", "purchaseType": "INDIVIDUAL", "selectedSessionId": "s-1"}
                        """)));
    }

    @Test
    @DisplayName("maps an UNAVAILABLE quote to available = false")
    void createQuote_mapsUnavailable() {
        stubFor(post(urlEqualTo(QUOTES_PATH)).willReturn(json(201, QUOTE_BODY.formatted("UNAVAILABLE"))));

        assertThat(adapter.createQuote("course-p", ClassPurchaseType.INDIVIDUAL, "s-1", TOKEN).available())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 422})
    @DisplayName("billing refusing to quote maps to QuoteRejectedException")
    void createQuote_onRefusal_isRejected(int status) {
        stubFor(post(urlEqualTo(QUOTES_PATH)).willReturn(json(status, "{\"title\":\"no\"}")));

        assertThatThrownBy(() -> adapter.createQuote("course-p", ClassPurchaseType.MONTHLY, null, TOKEN))
                .isInstanceOf(PhysicalPurchaseApiClient.QuoteRejectedException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    @DisplayName("throttling and server errors on quote map to ServiceUnavailableException")
    void createQuote_onServerError_isUnavailable(int status) {
        stubFor(post(urlEqualTo(QUOTES_PATH)).willReturn(json(status, "{}")));

        assertThatThrownBy(() -> adapter.createQuote("course-p", ClassPurchaseType.MONTHLY, null, TOKEN))
                .isInstanceOf(PhysicalPurchaseApiClient.ServiceUnavailableException.class);
    }

    // -- checkout --

    @Test
    @DisplayName("checks out with the bearer token and idempotency key, mapping the Mercado Pago URL")
    void checkout_mercadoPago() {
        stubFor(post(urlEqualTo(PURCHASES_PATH)).willReturn(json(201,
                """
                {"paymentId": "pay-1", "quoteId": "q-1", "status": "PENDING", "providerPreferenceId": "pref",
                 "checkoutUrl": "https://mp.example/checkout/pref", "externalReference": "SUB-pay-1",
                 "bankTransferInstructions": null}
                """)));

        ClassCheckout checkout = adapter.checkout("q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN);

        assertThat(checkout).isEqualTo(
                new ClassCheckout("pay-1", "PENDING", "https://mp.example/checkout/pref", null));
        verify(postRequestedFor(urlEqualTo(PURCHASES_PATH))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withRequestBody(equalToJson(
                        """
                        {"quoteId": "q-1", "paymentMethod": "MERCADO_PAGO", "idempotencyKey": "key-1"}
                        """)));
    }

    @Test
    @DisplayName("maps bank transfer instructions, unwrapping the amount")
    void checkout_bankTransfer() {
        stubFor(post(urlEqualTo(PURCHASES_PATH)).willReturn(json(201,
                """
                {"paymentId": "pay-2", "quoteId": "q-1", "status": "AWAITING_MANUAL_VERIFICATION",
                 "providerPreferenceId": null, "checkoutUrl": null, "externalReference": "SUB-pay-2",
                 "bankTransferInstructions": {"cbu": "0000003100000000000001", "alias": "menta.dance",
                   "holder": "Menta Dance SRL", "cuit": "30-12345678-9",
                   "amount": {"amount": 12000.00, "currency": "ARS"}, "reference": "SUB-pay-2"}}
                """)));

        ClassCheckout checkout = adapter.checkout("q-1", ClassPaymentMethod.BANK_TRANSFER, "key-1", TOKEN);

        assertThat(checkout.checkoutUrl()).isNull();
        assertThat(checkout.bankTransfer()).isEqualTo(new ClassCheckout.BankTransfer(
                "0000003100000000000001", "menta.dance", "Menta Dance SRL", "30-12345678-9",
                new BigDecimal("12000.00"), "ARS", "SUB-pay-2"));
    }

    @Test
    @DisplayName("a 409 maps to CapacityUnavailableException")
    void checkout_on409_isCapacityUnavailable() {
        stubFor(post(urlEqualTo(PURCHASES_PATH)).willReturn(json(409, "{\"code\":\"CAPACITY_UNAVAILABLE\"}")));

        assertThatThrownBy(() -> adapter.checkout("q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                .isInstanceOf(PhysicalPurchaseApiClient.CapacityUnavailableException.class);
    }

    @Test
    @DisplayName("a 410 maps to QuoteExpiredException")
    void checkout_on410_isQuoteExpired() {
        stubFor(post(urlEqualTo(PURCHASES_PATH)).willReturn(json(410, "{}")));

        assertThatThrownBy(() -> adapter.checkout("q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                .isInstanceOf(PhysicalPurchaseApiClient.QuoteExpiredException.class);
    }

    @Test
    @DisplayName("a 400 maps to QuoteRejectedException")
    void checkout_on400_isRejected() {
        stubFor(post(urlEqualTo(PURCHASES_PATH)).willReturn(json(400, "{}")));

        assertThatThrownBy(() -> adapter.checkout("q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                .isInstanceOf(PhysicalPurchaseApiClient.QuoteRejectedException.class);
    }

    @Test
    @DisplayName("an unexpected 401 maps to ServiceUnavailableException")
    void checkout_on401_isUnavailable() {
        stubFor(post(urlEqualTo(PURCHASES_PATH)).willReturn(json(401, "{}")));

        assertThatThrownBy(() -> adapter.checkout("q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                .isInstanceOf(PhysicalPurchaseApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a dropped connection maps to ServiceUnavailableException")
    void checkout_onConnectionReset_isUnavailable() {
        stubFor(post(urlEqualTo(PURCHASES_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter.checkout("q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                .isInstanceOf(PhysicalPurchaseApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("an empty 201 body maps to ServiceUnavailableException")
    void checkout_onEmptyBody_isUnavailable() {
        stubFor(post(urlEqualTo(PURCHASES_PATH)).willReturn(aResponse().withStatus(201)));

        assertThatThrownBy(() -> adapter.checkout("q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                .isInstanceOf(PhysicalPurchaseApiClient.ServiceUnavailableException.class);
    }
}
