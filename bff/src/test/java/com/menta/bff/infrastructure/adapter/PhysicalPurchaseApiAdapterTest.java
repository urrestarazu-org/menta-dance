package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.PaymentProgress;
import com.menta.bff.application.dto.PaymentProofFile;
import com.menta.bff.application.dto.PaymentStatusView;
import com.menta.bff.application.dto.PhysicalBankTransfer;
import com.menta.bff.application.dto.PhysicalCheckout;
import com.menta.bff.application.dto.PhysicalPurchaseType;
import com.menta.bff.application.dto.PhysicalQuote;
import com.menta.bff.application.dto.PurchaseFulfillment;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("PhysicalPurchaseApiAdapter")
class PhysicalPurchaseApiAdapterTest {

    private static final String QUOTES = "/api/v1/billing/physical/quotes";
    private static final String PURCHASES = "/api/v1/billing/physical/purchases";
    private static final String PAYMENT_ID = "00000000-0000-0000-0000-0000000000a1";
    private static final String PAYMENT = "/api/v1/billing/payments/" + PAYMENT_ID;
    private static final String QUOTE_ID = "00000000-0000-0000-0000-0000000000b1";
    private static final String TOKEN = "access-token-abc";
    private static final String BEARER = "Bearer " + TOKEN;

    private static final String QUOTE_BODY =
            """
            {"id": "%s", "courseId": "course-1", "purchaseType": "INDIVIDUAL", "scheduledSessionCount": 4,
             "selectedSessionId": "s-1", "amount": 12500.50, "currency": "ARS", "availability": "%s",
             "pricingVersion": 3, "createdAt": "2026-10-10T12:00:00Z", "expiresAt": "2026-10-10T13:00:00Z"}
            """;

    private PhysicalPurchaseApiAdapter adapter;
    private ListAppender<ILoggingEvent> logs;
    private Logger rootLogger;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        BillingApiProperties properties = new BillingApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(Duration.ofSeconds(2));
        adapter = new PhysicalPurchaseApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(),
                properties);
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>();
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(logs);
    }

    private static void stubStatus(com.github.tomakehurst.wiremock.client.MappingBuilder mapping, int status,
            String code) {
        stubFor(mapping.willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":" + status + ",\"code\":\"" + code + "\"}")));
    }

    @Test
    @DisplayName("an individual quote sends the bearer token and the session, and maps the response")
    void individualQuote() {
        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody(QUOTE_BODY.formatted(QUOTE_ID, "AVAILABLE"))));

        PhysicalQuote quote = adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.INDIVIDUAL, "s-1");

        assertThat(quote).isEqualTo(new PhysicalQuote(QUOTE_ID, PhysicalPurchaseType.INDIVIDUAL, 4, "s-1",
                new BigDecimal("12500.50"), "ARS", false, Instant.parse("2026-10-10T13:00:00Z")));
        verify(postRequestedFor(urlEqualTo(QUOTES)).withHeader("Authorization", equalTo(BEARER))
                .withRequestBody(matchingJsonPath("$.courseId", equalTo("course-1")))
                .withRequestBody(matchingJsonPath("$.purchaseType", equalTo("INDIVIDUAL")))
                .withRequestBody(matchingJsonPath("$.selectedSessionId", equalTo("s-1"))));
    }

    @Test
    @DisplayName("a monthly quote omits the session and reads UNAVAILABLE as unavailable")
    void monthlyQuoteUnavailable() {
        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(QUOTE_BODY.formatted(QUOTE_ID, "UNAVAILABLE"))));

        PhysicalQuote quote = adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null);

        assertThat(quote.unavailable()).isTrue();
        verify(postRequestedFor(urlEqualTo(QUOTES)).withRequestBody(matchingJsonPath("$.purchaseType",
                equalTo("MONTHLY"))).withRequestBody(matchingJsonPath("$[?(!@.selectedSessionId)]")));
    }

    @ParameterizedTest(name = "quote {0} {1} -> {2}")
    @CsvSource({
        "422, NO_SCHEDULED_SESSIONS, NoScheduledSessionsException",
        "422, SELECTED_SESSION_REQUIRED, InvalidRequestException",
        "409, NO_NEW_SESSIONS, NoNewSessionsException",
        "409, CAPACITY_UNAVAILABLE, CapacityUnavailableException",
        "409, SOMETHING_NEW, CapacityUnavailableException",
        "422, INDIVIDUAL_SURCHARGE_TOO_SMALL, InvalidRequestException",
        "400, INVALID_REQUEST, InvalidRequestException",
        "404, PHYSICAL_COURSE_PRICING_NOT_FOUND, NotFoundException",
        "401, UNAUTHORIZED, ServiceUnavailableException",
        "500, INTERNAL, ServiceUnavailableException",
        "418, TEAPOT, ServiceUnavailableException"})
    void quoteErrors(int status, String code, String expected) {
        stubStatus(post(urlEqualTo(QUOTES)), status, code);

        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(RuntimeException.class).hasMessageNotContaining(TOKEN)
                .satisfies(e -> assertThat(e.getClass().getSimpleName()).isEqualTo(expected));
    }

    @Test
    @DisplayName("a checkout sends the quote, Mercado Pago and the idempotency key, and returns the checkout")
    void checkout() {
        stubFor(post(urlEqualTo(PURCHASES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(
                        """
                        {"paymentId": "%s", "quoteId": "%s", "status": "PENDING", "providerPreferenceId": "pref-1",
                         "checkoutUrl": "https://pay.example.com/checkout/1", "externalReference": "ref-1",
                         "bankTransferInstructions": null}
                        """.formatted(PAYMENT_ID, QUOTE_ID))));

        PhysicalCheckout checkout = adapter.createCheckout(TOKEN, QUOTE_ID, "key-1");

        assertThat(checkout).isEqualTo(new PhysicalCheckout(PAYMENT_ID, "https://pay.example.com/checkout/1"));
        verify(postRequestedFor(urlEqualTo(PURCHASES)).withHeader("Authorization", equalTo(BEARER))
                .withRequestBody(matchingJsonPath("$.quoteId", equalTo(QUOTE_ID)))
                .withRequestBody(matchingJsonPath("$.paymentMethod", equalTo("MERCADO_PAGO")))
                .withRequestBody(matchingJsonPath("$.idempotencyKey", equalTo("key-1"))));
    }

    @Test
    @DisplayName("a checkout answer without a checkout URL is treated as unavailable")
    void checkoutWithoutUrl() {
        stubFor(post(urlEqualTo(PURCHASES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody("{\"paymentId\": \"" + PAYMENT_ID + "\"}")));

        assertThatThrownBy(() -> adapter.createCheckout(TOKEN, QUOTE_ID, "key-1"))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @ParameterizedTest(name = "checkout {0} {1} -> {2}")
    @CsvSource({
        "409, NO_NEW_SESSIONS, NoNewSessionsException",
        "409, CAPACITY_UNAVAILABLE, CapacityUnavailableException",
        "409, SOMETHING_NEW, CapacityUnavailableException",
        "410, PHYSICAL_COURSE_QUOTE_EXPIRED, QuoteExpiredException",
        "400, INVALID_REQUEST, InvalidRequestException",
        "503, PAYMENT_PREFERENCE_UNAVAILABLE, ServiceUnavailableException",
        "401, UNAUTHORIZED, ServiceUnavailableException"})
    void checkoutErrors(int status, String code, String expected) {
        stubStatus(post(urlEqualTo(PURCHASES)), status, code);

        assertThatThrownBy(() -> adapter.createCheckout(TOKEN, QUOTE_ID, "key-1"))
                .satisfies(e -> assertThat(e.getClass().getSimpleName()).isEqualTo(expected));
    }

    @Test
    @DisplayName("a 409 without a readable problem body keeps meaning no capacity")
    void conflictWithoutCodeIsCapacityUnavailable() {
        stubFor(post(urlEqualTo(PURCHASES)).willReturn(aResponse().withStatus(409)));
        assertThatThrownBy(() -> adapter.createCheckout(TOKEN, QUOTE_ID, "key-1"))
                .isInstanceOf(PhysicalPurchaseApiClient.CapacityUnavailableException.class);

        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(409).withBody("<html>conflict</html>")));
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(PhysicalPurchaseApiClient.CapacityUnavailableException.class);
    }

    @ParameterizedTest(name = "payment status {0} -> {1}")
    @CsvSource({
        "COMPLETED, COMPLETED", "REJECTED, REJECTED", "CANCELLED, CANCELLED", "EXPIRED, EXPIRED",
        "AWAITING_PROVIDER, PENDING", "AWAITING_MANUAL_VERIFICATION, PENDING",
        "RECONCILIATION_REQUIRED, PENDING", "SOMETHING_NEW, PENDING"})
    void paymentStatus(String upstream, PaymentProgress expected) {
        stubFor(get(urlEqualTo(PAYMENT)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"" + upstream
                        + "\",\"createdAt\":\"2026-10-10T12:00:00Z\",\"updatedAt\":\"2026-10-10T12:01:00Z\"}")));

        assertThat(adapter.getPaymentProgress(TOKEN, PAYMENT_ID).progress()).isEqualTo(expected);
        verify(getRequestedFor(urlEqualTo(PAYMENT)).withHeader("Authorization", equalTo(BEARER)));
    }

    @ParameterizedTest(name = "fulfillment {0} -> {1}")
    @CsvSource(value = {"EXCEPTION, EXCEPTION", "RESOLVED, RESOLVED", "ASSIGNED, ASSIGNED",
        "SOMETHING_NEW, null"}, nullValues = "null")
    @DisplayName("a completed payment carries the purchase fulfillment, unknown reads as null")
    void paymentFulfillment(String upstream, PurchaseFulfillment expected) {
        stubFor(get(urlEqualTo(PAYMENT)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"COMPLETED\",\"fulfillment\":\"" + upstream + "\"}")));

        assertThat(adapter.getPaymentProgress(TOKEN, PAYMENT_ID))
                .isEqualTo(new PaymentStatusView(PaymentProgress.COMPLETED, expected));
    }

    @Test
    @DisplayName("an absent or null fulfillment field reads as null")
    void paymentWithoutFulfillment() {
        stubFor(get(urlEqualTo(PAYMENT)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"status\":\"COMPLETED\"}")));
        assertThat(adapter.getPaymentProgress(TOKEN, PAYMENT_ID).fulfillment()).isNull();

        stubFor(get(urlEqualTo(PAYMENT)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"COMPLETED\",\"fulfillment\":null}")));
        assertThat(adapter.getPaymentProgress(TOKEN, PAYMENT_ID).fulfillment()).isNull();
    }

    @ParameterizedTest(name = "fulfillment {0} reads as null without failing the payment status")
    @ValueSource(strings = {"{\"code\":\"EXCEPTION\"}", "[\"EXCEPTION\"]", "42"})
    @DisplayName("a fulfillment that is not a string degrades to null instead of erroring")
    void paymentWithUnmappableFulfillment(String fulfillmentJson) {
        stubFor(get(urlEqualTo(PAYMENT)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"COMPLETED\",\"fulfillment\":" + fulfillmentJson + "}")));

        assertThat(adapter.getPaymentProgress(TOKEN, PAYMENT_ID))
                .isEqualTo(new PaymentStatusView(PaymentProgress.COMPLETED, null));
    }

    @Test
    @DisplayName("a payment that is not the caller's (404) surfaces as not found")
    void paymentNotFound() {
        stubStatus(get(urlEqualTo(PAYMENT)), 404, "PAYMENT_NOT_FOUND");

        assertThatThrownBy(() -> adapter.getPaymentProgress(TOKEN, PAYMENT_ID))
                .isInstanceOf(BillingApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("a payment status outage (5xx) and a dropped connection are unavailable")
    void paymentOutage() {
        stubFor(get(urlEqualTo(PAYMENT)).willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> adapter.getPaymentProgress(TOKEN, PAYMENT_ID))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);

        stubFor(get(urlEqualTo(PAYMENT)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.getPaymentProgress(TOKEN, PAYMENT_ID))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class).hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("429 carries the Retry-After seconds, and an unparsable value is dropped")
    void rateLimited() {
        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));

        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOfSatisfying(BillingApiClient.ServiceUnavailableException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(30L));

        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "soon")));

        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOfSatisfying(BillingApiClient.ServiceUnavailableException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isNull());
    }

    @Test
    @DisplayName("an empty or non-JSON error body and a dropped connection are unavailable, never a leak")
    void garbageAndTransportErrors() {
        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(502).withBody("<html>bad gateway</html>")));
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);

        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(200)));
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);

        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("the access token never reaches the logs, even when the call fails")
    void tokenIsNeverLogged() {
        stubStatus(post(urlEqualTo(QUOTES)), 500, "INTERNAL");
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "c", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(RuntimeException.class);
        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "c", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(RuntimeException.class);

        stubStatus(post(urlEqualTo(PURCHASES)), 418, "TEAPOT");
        assertThatThrownBy(() -> adapter.createCheckout(TOKEN, QUOTE_ID, "k")).isInstanceOf(RuntimeException.class);

        stubFor(get(urlEqualTo(PAYMENT)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.getPaymentProgress(TOKEN, PAYMENT_ID)).isInstanceOf(RuntimeException.class);

        assertThat(logs.list).isNotEmpty().allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain(TOKEN).doesNotContain("Authorization"));
    }

    private static final String TRANSFER_BODY =
            """
            {"paymentId": "%s", "quoteId": "%s", "status": "PENDING", "providerPreferenceId": null,
             "checkoutUrl": null, "externalReference": "PHY-BT-1",
             "bankTransferInstructions": {"cbu": "0000003100010000000001", "alias": "menta.dance",
               "holder": "Menta Dance SRL", "cuit": "30-12345678-9",
               "amount": {"amount": 12500.50, "currency": "ARS"}, "reference": "PHY-BT-1"}}
            """;
    private static final String PROOF = PAYMENT + "/proof";

    @Test
    @DisplayName("a bank transfer checkout asks for BANK_TRANSFER and maps the instructions")
    void bankTransferCheckout() {
        stubFor(post(urlEqualTo(PURCHASES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(TRANSFER_BODY.formatted(PAYMENT_ID, QUOTE_ID))));

        PhysicalBankTransfer transfer = adapter.createBankTransferCheckout(TOKEN, QUOTE_ID, "key-1");

        assertThat(transfer).isEqualTo(new PhysicalBankTransfer(PAYMENT_ID, "0000003100010000000001",
                "menta.dance", "Menta Dance SRL", "30-12345678-9", new BigDecimal("12500.50"), "ARS", "PHY-BT-1"));
        verify(postRequestedFor(urlEqualTo(PURCHASES)).withHeader("Authorization", equalTo(BEARER))
                .withRequestBody(matchingJsonPath("$.paymentMethod", equalTo("BANK_TRANSFER")))
                .withRequestBody(matchingJsonPath("$.idempotencyKey", equalTo("key-1"))));
    }

    @Test
    @DisplayName("a bank transfer checkout without instructions is treated as unavailable")
    void bankTransferWithoutInstructions() {
        stubFor(post(urlEqualTo(PURCHASES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"paymentId\": \"" + PAYMENT_ID + "\", \"bankTransferInstructions\": null}")));

        assertThatThrownBy(() -> adapter.createBankTransferCheckout(TOKEN, QUOTE_ID, "key-1"))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @ParameterizedTest(name = "bank transfer checkout {0} {1} -> {2}")
    @CsvSource({
        "409, NO_NEW_SESSIONS, NoNewSessionsException",
        "409, CAPACITY_UNAVAILABLE, CapacityUnavailableException",
        "410, PHYSICAL_COURSE_QUOTE_EXPIRED, QuoteExpiredException",
        "400, INVALID_REQUEST, InvalidRequestException",
        "503, UNAVAILABLE, ServiceUnavailableException"})
    void bankTransferErrors(int status, String code, String expected) {
        stubStatus(post(urlEqualTo(PURCHASES)), status, code);

        assertThatThrownBy(() -> adapter.createBankTransferCheckout(TOKEN, QUOTE_ID, "key-1"))
                .satisfies(e -> assertThat(e.getClass().getSimpleName()).isEqualTo(expected));
    }

    @Test
    @DisplayName("a proof is sent as multipart field file with the bearer token")
    void proofUpload() {
        stubFor(post(urlEqualTo(PROOF)).willReturn(aResponse().withStatus(200)));

        adapter.submitPaymentProof(TOKEN, PAYMENT_ID, new PaymentProofFile("recibo.png", "image/png",
                new byte[] {1, 2, 3}));

        verify(postRequestedFor(urlEqualTo(PROOF)).withHeader("Authorization", equalTo(BEARER))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withRequestBody(containing("name=\"file\""))
                .withRequestBody(containing("filename=\"recibo.png\""))
                .withRequestBody(containing("image/png")));
    }

    @ParameterizedTest(name = "proof {0} -> {1}")
    @CsvSource({
        "400, ProofRejectedException",
        "404, NotFoundException",
        "429, TooManyProofsException",
        "503, ServiceUnavailableException",
        "401, ServiceUnavailableException"})
    void proofErrors(int status, String expected) {
        stubStatus(post(urlEqualTo(PROOF)), status, "SOME_CODE");

        assertThatThrownBy(() -> adapter.submitPaymentProof(TOKEN, PAYMENT_ID,
                new PaymentProofFile("r.pdf", "application/pdf", new byte[] {1})))
                .satisfies(e -> assertThat(e.getClass().getSimpleName()).isEqualTo(expected))
                .hasMessageNotContaining(TOKEN);
    }
}
