package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.CorrectionDecision;
import com.menta.bff.application.dto.ExceptionPurchase;
import com.menta.bff.application.dto.ExceptionPurchasePage;
import com.menta.bff.application.dto.PaymentDetail;
import com.menta.bff.application.dto.PaymentModality;
import com.menta.bff.application.dto.PaymentStatusType;
import com.menta.bff.application.dto.PendingPayment;
import com.menta.bff.application.dto.PendingPaymentPage;
import com.menta.bff.application.dto.PurchaseResolution;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException.Code;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("AdminBillingApiAdapter")
class AdminBillingApiAdapterTest {

    private static final String PAYMENT_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String USER_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String BASE = "/api/v1/admin/billing/payments";
    private static final String PAYMENT_PATH = BASE + "/" + PAYMENT_ID;
    private static final String PURCHASE_ID = "7a1b2c3d-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String PURCHASES = "/api/v1/admin/billing/purchases";
    private static final String RESOLUTION_PATH = PURCHASES + "/" + PURCHASE_ID + "/resolution";
    private static final String TOKEN = "access-token-abc";
    private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    private static final String ITEM_JSON =
            """
            {"paymentId": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "userId": "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00",
             "targetModality": "PHYSICAL", "targetReference": "course-1", "amount": 25000.50, "currency": "ARS",
             "statusType": "AWAITING_MANUAL_VERIFICATION", "hasProof": true, "createdAt": "2026-11-20T15:30:00Z"}
            """;
    private static final String PAGE_JSON =
            "{\"items\": [" + ITEM_JSON + "], \"page\": 2, \"size\": 50, \"totalElements\": 101, \"totalPages\": 3}";
    private static final String DETAIL_JSON =
            """
            {"paymentId": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "userId": "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00",
             "targetModality": "VIRTUAL", "targetReference": "plan-1", "amount": 9000, "currency": "ARS",
             "statusType": "RECONCILIATION_REQUIRED", "createdAt": "2026-11-20T15:30:00Z",
             "proofUrl": "/api/v1/billing/payments/5b6f7c8e/proof?token=SIGNED-SECRET"}
            """;

    /** One purchase of the API's list; {@code value} replaces the field, or removes it when it is null. */
    private static String purchaseJson(String field, String value) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("purchaseId", "\"" + PURCHASE_ID + "\"");
        fields.put("paymentId", "\"" + PAYMENT_ID + "\"");
        fields.put("userId", "\"" + USER_ID + "\"");
        fields.put("targetModality", "\"PHYSICAL\"");
        fields.put("targetReference", "\"course-1\"");
        fields.put("amount", "25000.50");
        fields.put("currency", "\"ARS\"");
        fields.put("createdAt", "\"2026-11-20T15:30:00Z\"");
        fields.put("physicalSessionIds", "[\"s-1\", \"s-2\"]");
        if (value == null) {
            fields.remove(field);
        } else {
            fields.put(field, value);
        }
        return fields.entrySet().stream().map(e -> "\"" + e.getKey() + "\": " + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String purchasesPage(String... items) {
        return "{\"items\": [" + String.join(",", items)
                + "], \"page\": 1, \"size\": 50, \"totalElements\": 51, \"totalPages\": 2}";
    }

    private AdminBillingApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        adapter = adapterWith(wireMock, GENEROUS_TIMEOUT);
    }

    private static AdminBillingApiAdapter adapterWith(WireMockRuntimeInfo wireMock, Duration timeout) {
        BillingApiProperties properties = new BillingApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(timeout);
        return new AdminBillingApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stub(String method, String path, int status, String body) {
        stubFor(request(method, urlPathEqualTo(path)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static String problem(String code) {
        return ("{\"type\":\"https://menta.dance/problems/x\",\"status\":409,\"detail\":\"secret detail\","
                + "\"code\":\"%s\"}").formatted(code);
    }

    // ---- every call: one operation per row ----

    private record Operation(String name, String method, String path, String okBody, boolean readsBody,
            Consumer<AdminBillingApiAdapter> call) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static List<Operation> operations() {
        return List.of(
                new Operation("listPendingPayments", "GET", BASE, PAGE_JSON, true,
                        a -> a.listPendingPayments(TOKEN, 2, 50)),
                new Operation("getPaymentDetail", "GET", PAYMENT_PATH, DETAIL_JSON, true,
                        a -> a.getPaymentDetail(TOKEN, PAYMENT_ID)),
                new Operation("approvePayment", "POST", PAYMENT_PATH + "/approve", "", false,
                        a -> a.approvePayment(TOKEN, PAYMENT_ID)),
                new Operation("rejectPayment", "POST", PAYMENT_PATH + "/reject", "", false,
                        a -> a.rejectPayment(TOKEN, PAYMENT_ID, "Monto incorrecto")),
                new Operation("correctPayment", "POST", PAYMENT_PATH + "/corrections", "", false,
                        a -> a.correctPayment(TOKEN, PAYMENT_ID, CorrectionDecision.APPROVED, "Motivo", "Evidencia")),
                new Operation("listPurchasesInException", "GET", PURCHASES, purchasesPage(purchaseJson(null, null)),
                        true, a -> a.listPurchasesInException(TOKEN, 1, 50)),
                new Operation("resolvePurchase", "POST", RESOLUTION_PATH, "{\"status\": \"RESOLVED\"}", false,
                        a -> a.resolvePurchase(TOKEN, PURCHASE_ID, PurchaseResolution.REFUNDED, "Reembolso")));
    }

    static Stream<Arguments> everyOperation() {
        return operations().stream().map(Arguments::of);
    }

    static Stream<Arguments> everyOperationAndCode() {
        // PROOF_NOT_FOUND is the BFF's own code: the API never answers it.
        return operations().stream().flatMap(operation -> Stream.of(Code.values())
                .filter(code -> code != Code.PROOF_NOT_FOUND).map(code -> Arguments.of(operation, code)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperation")
    @DisplayName("every call sends the bearer token, and a 200 succeeds")
    void sendsTheBearerToken(Operation operation) {
        stub(operation.method(), operation.path(), 200, operation.okBody());

        operation.call().accept(adapter);

        verify(anyRequestedFor(urlPathEqualTo(operation.path()))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @ParameterizedTest(name = "{0} refused with {1}")
    @MethodSource("everyOperationAndCode")
    @DisplayName("an expected code in a 400, 404 or 409 is a typed failure, with nothing of the reply in it")
    void typedRefusals(Operation operation, Code code) {
        int status = switch (code) {
            case INVALID_REQUEST -> 400;
            case PAYMENT_NOT_FOUND, PURCHASE_NOT_FOUND -> 404;
            case ILLEGAL_PAYMENT_STATE_TRANSITION, ILLEGAL_PURCHASE_STATE_TRANSITION, PURCHASE_ALREADY_RESOLVED -> 409;
            case PROOF_NOT_FOUND -> throw new IllegalArgumentException("not an API code");
        };
        stub(operation.method(), operation.path(), status, problem(code.name()));

        assertThatThrownBy(() -> operation.call().accept(adapter))
                .isInstanceOfSatisfying(PaymentRefusalException.class, e -> assertThat(e.getCode()).isEqualTo(code))
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(PAYMENT_ID);
    }

    static Stream<Arguments> unexpectedAnswers() {
        return operations().stream().flatMap(operation -> Stream.of(
                Arguments.of(operation, 201, "{}"),
                Arguments.of(operation, 204, ""),
                Arguments.of(operation, 401, problem("UNAUTHENTICATED")),
                Arguments.of(operation, 403, problem("ACCESS_DENIED")),
                Arguments.of(operation, 429, problem("RATE_LIMITED")),
                Arguments.of(operation, 500, problem("INTERNAL")),
                Arguments.of(operation, 503, "<html>down</html>"),
                // a refusal whose code is not one of the expected ones, or that has none, or no readable body
                Arguments.of(operation, 400, problem("SOMETHING_NEW")),
                Arguments.of(operation, 404, problem("PROOF_NOT_FOUND")),
                Arguments.of(operation, 404, "{\"detail\":\"secret detail\"}"),
                Arguments.of(operation, 409, problem("")),
                Arguments.of(operation, 409, "<html>conflict</html>"),
                Arguments.of(operation, 409, "")));
    }

    @ParameterizedTest(name = "[{index}] {0} answered {1}")
    @MethodSource("unexpectedAnswers")
    @DisplayName("everything unexpected is unavailable, with nothing of the reply or the token in the failure")
    void unexpectedIsUnavailable(Operation operation, int status, String body) {
        stub(operation.method(), operation.path(), status, body);

        assertThatThrownBy(() -> operation.call().accept(adapter))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("a read answered with an unusable or incomplete body is unavailable")
    void unusableSuccessIsUnavailable() {
        for (String body : List.of("{}", "", "<html>not json</html>", "{\"items\": [{}], \"page\": 0}")) {
            stub("GET", BASE, 200, body);
            assertThatThrownBy(() -> adapter.listPendingPayments(TOKEN, 0, 20)).as(body)
                    .isInstanceOf(ServiceUnavailableException.class);
        }
        for (String body : List.of("{}", "", DETAIL_JSON.replace("\"currency\": \"ARS\",", ""))) {
            stub("GET", PAYMENT_PATH, 200, body);
            assertThatThrownBy(() -> adapter.getPaymentDetail(TOKEN, PAYMENT_ID)).as(body)
                    .isInstanceOf(ServiceUnavailableException.class);
        }
    }

    @Test
    @DisplayName("a write succeeds on a 200 whatever its body, which is never read")
    void writeBodyIsIgnored() {
        stub("POST", PAYMENT_PATH + "/approve", 200, "<html>whatever</html>");

        adapter.approvePayment(TOKEN, PAYMENT_ID);

        verify(postRequestedFor(urlPathEqualTo(PAYMENT_PATH + "/approve")));
    }

    @Test
    @DisplayName("a dropped connection and a response slower than the timeout are unavailable")
    void transportErrorsAndTimeout(WireMockRuntimeInfo wireMock) {
        stubFor(request("GET", urlPathEqualTo(BASE))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.listPendingPayments(TOKEN, 0, 20))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageNotContaining(TOKEN);

        stubFor(request("POST", urlPathEqualTo(PAYMENT_PATH + "/approve")).willReturn(aResponse().withStatus(200)
                .withFixedDelay(2_000)));
        assertThatThrownBy(() -> adapterWith(wireMock, SHORT_TIMEOUT).approvePayment(TOKEN, PAYMENT_ID))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageNotContaining(TOKEN);
    }

    // ---- what each call sends and returns ----

    @Test
    @DisplayName("listPendingPayments sends the two fixed filters and the paging, and maps every field")
    void listPendingPayments() {
        stub("GET", BASE, 200, PAGE_JSON);

        PendingPaymentPage page = adapter.listPendingPayments(TOKEN, 2, 50);

        assertThat(page).isEqualTo(new PendingPaymentPage(List.of(new PendingPayment(PAYMENT_ID, USER_ID,
                PaymentModality.PHYSICAL, "course-1", new BigDecimal("25000.50"), "ARS",
                PaymentStatusType.AWAITING_MANUAL_VERIFICATION, true, Instant.parse("2026-11-20T15:30:00Z"))),
                2, 50, 101, 3));
        verify(getRequestedFor(urlPathEqualTo(BASE))
                .withQueryParam("status", equalTo("PENDING"))
                .withQueryParam("substatus", equalTo("AWAITING_MANUAL_VERIFICATION"))
                .withQueryParam("page", equalTo("2"))
                .withQueryParam("size", equalTo("50")));
    }

    @Test
    @DisplayName("an empty page keeps its totals, and a modality or status this BFF does not know reads as UNKNOWN")
    void emptyPageAndUnknownValues() {
        stub("GET", BASE, 200, "{\"items\": [], \"page\": 4, \"size\": 20, \"totalElements\": 3, \"totalPages\": 1}");
        assertThat(adapter.listPendingPayments(TOKEN, 4, 20))
                .isEqualTo(new PendingPaymentPage(List.of(), 4, 20, 3, 1));

        stub("GET", BASE, 200, PAGE_JSON.replace("PHYSICAL", "HOLOGRAM")
                .replace("\"statusType\": \"AWAITING_MANUAL_VERIFICATION\"", "\"statusType\": \"LIMBO\""));
        assertThat(adapter.listPendingPayments(TOKEN, 0, 20).items()).singleElement().satisfies(item -> {
            assertThat(item.targetModality()).isEqualTo(PaymentModality.UNKNOWN);
            assertThat(item.statusType()).isEqualTo(PaymentStatusType.UNKNOWN);
        });
    }

    @Test
    @DisplayName("an item without a field the BFF needs makes the whole page unavailable")
    void incompleteItem() {
        stub("GET", BASE, 200, PAGE_JSON.replace("\"hasProof\": true,", ""));

        assertThatThrownBy(() -> adapter.listPendingPayments(TOKEN, 0, 20))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("getPaymentDetail maps every field and exposes only whether there is a proof, never its URL")
    void getPaymentDetail() {
        stub("GET", PAYMENT_PATH, 200, DETAIL_JSON);

        PaymentDetail detail = adapter.getPaymentDetail(TOKEN, PAYMENT_ID);

        assertThat(detail).isEqualTo(new PaymentDetail(PAYMENT_ID, USER_ID, PaymentModality.VIRTUAL, "plan-1",
                new BigDecimal("9000"), "ARS", PaymentStatusType.RECONCILIATION_REQUIRED,
                Instant.parse("2026-11-20T15:30:00Z"), true));
        assertThat(detail.toString()).doesNotContain("SIGNED-SECRET");
        assertThat(PaymentDetail.class.getRecordComponents()).extracting(RecordComponent::getName)
                .noneMatch(name -> name.toLowerCase().contains("url"));
    }

    @Test
    @DisplayName("a null or absent proofUrl means there is no proof")
    void noProof() {
        stub("GET", PAYMENT_PATH, 200, DETAIL_JSON.replaceAll("\"proofUrl\": \"[^\"]*\"", "\"proofUrl\": null"));
        assertThat(adapter.getPaymentDetail(TOKEN, PAYMENT_ID).hasProof()).isFalse();
    }

    @Test
    @DisplayName("approvePayment is a POST without a body")
    void approvePayment() {
        stub("POST", PAYMENT_PATH + "/approve", 200, "");

        adapter.approvePayment(TOKEN, PAYMENT_ID);

        assertThat(findAll(postRequestedFor(urlPathEqualTo(PAYMENT_PATH + "/approve"))))
                .singleElement().satisfies(request -> assertThat(request.getBodyAsString()).isEmpty());
    }

    @Test
    @DisplayName("rejectPayment sends the reason as JSON")
    void rejectPayment() {
        stub("POST", PAYMENT_PATH + "/reject", 200, "");

        adapter.rejectPayment(TOKEN, PAYMENT_ID, "Monto incorrecto");

        verify(postRequestedFor(urlPathEqualTo(PAYMENT_PATH + "/reject"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{\"reason\": \"Monto incorrecto\"}", false, false)));
    }

    @Test
    @DisplayName("correctPayment sends the decision, the reason and the evidence as JSON")
    void correctPayment() {
        stub("POST", PAYMENT_PATH + "/corrections", 200, "");

        adapter.correctPayment(TOKEN, PAYMENT_ID, CorrectionDecision.REJECTED, "Motivo", "Evidencia");

        verify(postRequestedFor(urlPathEqualTo(PAYMENT_PATH + "/corrections"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson(
                        "{\"decision\": \"REJECTED\", \"reason\": \"Motivo\", \"evidence\": \"Evidencia\"}",
                        false, false)));
    }

    // ---- the purchases in exception (#370) ----

    @Test
    @DisplayName("listPurchasesInException sends status=EXCEPTION and the paging, and maps every field")
    void listPurchasesInException() {
        stub("GET", PURCHASES, 200, purchasesPage(purchaseJson(null, null), purchaseJson("targetModality", "\"HOLO\""),
                purchaseJson("physicalSessionIds", "[]")));

        ExceptionPurchasePage page = adapter.listPurchasesInException(TOKEN, 1, 50);

        assertThat(page.items().get(0)).isEqualTo(new ExceptionPurchase(PURCHASE_ID, USER_ID,
                PaymentModality.PHYSICAL, new BigDecimal("25000.50"), "ARS", Instant.parse("2026-11-20T15:30:00Z"),
                List.of("s-1", "s-2")));
        assertThat(page.items().get(1).targetModality()).isEqualTo(PaymentModality.UNKNOWN);
        assertThat(page.items().get(2).physicalSessionIds()).isEmpty();
        assertThat(page).extracting(ExceptionPurchasePage::page, ExceptionPurchasePage::size,
                ExceptionPurchasePage::totalElements, ExceptionPurchasePage::totalPages)
                .containsExactly(1, 50, 51L, 2);
        verify(getRequestedFor(urlPathEqualTo(PURCHASES))
                .withQueryParam("status", equalTo("EXCEPTION"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("size", equalTo("50")));
    }

    static Stream<Arguments> incompletePurchases() {
        // physicalSessionIds is required by the contract (never null): absent or null is not guessed as "none".
        return Stream.of("purchaseId", "userId", "targetModality", "amount", "currency", "createdAt",
                "physicalSessionIds").map(Arguments::of);
    }

    @ParameterizedTest(name = "without {0}")
    @MethodSource("incompletePurchases")
    @DisplayName("a purchase without a required field makes the whole page unavailable")
    void incompletePurchase(String field) {
        stub("GET", PURCHASES, 200, purchasesPage(purchaseJson(field, null)));
        assertThatThrownBy(() -> adapter.listPurchasesInException(TOKEN, 0, 20))
                .isInstanceOf(ServiceUnavailableException.class);

        stub("GET", PURCHASES, 200, purchasesPage(purchaseJson(field, "null")));
        assertThatThrownBy(() -> adapter.listPurchasesInException(TOKEN, 0, 20))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("an empty or unusable page of purchases keeps its totals or is unavailable")
    void purchasesPageShapes() {
        stub("GET", PURCHASES, 200, "{\"items\": [], \"page\": 3, \"size\": 20, \"totalElements\": 2, "
                + "\"totalPages\": 1}");
        assertThat(adapter.listPurchasesInException(TOKEN, 3, 20))
                .isEqualTo(new ExceptionPurchasePage(List.of(), 3, 20, 2, 1));

        for (String body : List.of("{}", "", "<html>no</html>", "{\"items\": [], \"page\": 0}")) {
            stub("GET", PURCHASES, 200, body);
            assertThatThrownBy(() -> adapter.listPurchasesInException(TOKEN, 0, 20)).as(body)
                    .isInstanceOf(ServiceUnavailableException.class);
        }
    }

    @Test
    @DisplayName("resolvePurchase sends the resolution and the note as JSON")
    void resolvePurchase() {
        stub("POST", RESOLUTION_PATH, 200, "{\"status\": \"RESOLVED\"}");

        adapter.resolvePurchase(TOKEN, PURCHASE_ID, PurchaseResolution.COMPENSATED, "Se compensó con una clase");

        verify(postRequestedFor(urlPathEqualTo(RESOLUTION_PATH))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson(
                        "{\"resolution\": \"COMPENSATED\", \"note\": \"Se compensó con una clase\"}", false, false)));
    }
}
