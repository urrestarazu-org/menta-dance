package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.PaymentProof;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException.Code;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;
import com.menta.bff.infrastructure.config.AuthProperties;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import com.menta.bff.infrastructure.config.WebClientConfig;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * {@code AdminBillingApiAdapter.fetchPaymentProof} (#370): the two-call flow (detail with the bearer token, then
 * the signed link without it), the checks that keep the BFF from following anything but the expected link, and
 * the answer limits. The WebClient is the real {@code billingApiWebClient} bean, so its memory limit is the one
 * production runs with.
 */
@WireMockTest
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("AdminBillingApiAdapter proof")
class AdminBillingApiAdapterProofTest {

    private static final String PAYMENT_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String DETAIL_PATH = "/api/v1/admin/billing/payments/" + PAYMENT_ID;
    private static final String PROOF_PATH = "/api/v1/billing/payments/" + PAYMENT_ID + "/proof";
    private static final String SIGNED_TOKEN = "fake-proof_one.fake-proof_two";
    private static final String PROOF_URL = PROOF_PATH + "?token=" + SIGNED_TOKEN;
    private static final String ACCESS_TOKEN = "access-token-abc";
    private static final byte[] BYTES = {1, 2, 3, (byte) 0xFF};
    private static final int MB = 1024 * 1024;

    private AdminBillingApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        BillingApiProperties billing = new BillingApiProperties();
        billing.setBaseUrl(wireMock.getHttpBaseUrl());
        billing.setTimeout(Duration.ofSeconds(10));
        adapter = new AdminBillingApiAdapter(
                new WebClientConfig(new AuthProperties(), new VirtualApiProperties(), billing)
                        .billingApiWebClient(),
                billing);
    }

    private static void stubDetail(String proofUrlJson) {
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(("{\"paymentId\":\"%s\",\"userId\":\"u-1\",\"targetModality\":\"VIRTUAL\","
                        + "\"targetReference\":\"plan-1\",\"amount\":9000,\"currency\":\"ARS\","
                        + "\"statusType\":\"AWAITING_MANUAL_VERIFICATION\",\"createdAt\":\"2026-11-20T15:30:00Z\","
                        + "\"proofUrl\":%s}").formatted(PAYMENT_ID, proofUrlJson))));
    }

    private static void stubDetailWithProof(String proofUrl) {
        stubDetail("\"" + proofUrl + "\"");
    }

    private static void stubProof(int status, String contentType, byte[] body) {
        var response = aResponse().withStatus(status).withBody(body);
        if (contentType != null) {
            response.withHeader("Content-Type", contentType);
        }
        stubFor(get(urlPathEqualTo(PROOF_PATH)).willReturn(response));
    }

    private static void verifyNoProofCall() {
        verify(1, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"image/png", "image/jpeg", "application/pdf"})
    @DisplayName("fetches the proof in two calls: the detail with the bearer token, the proof without it")
    void fetchesTheProof(String contentType) {
        stubDetailWithProof(PROOF_URL);
        stubProof(200, contentType, BYTES);

        PaymentProof proof = adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID);

        assertThat(proof.content()).isEqualTo(BYTES);
        assertThat(proof.contentType()).isEqualTo(contentType);
        verify(1, getRequestedFor(urlEqualTo(DETAIL_PATH))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
        // The token reaches the proof endpoint exactly as signed (base64url and dots, no double encoding), and
        // the administrator's bearer token never does.
        verify(1, getRequestedFor(urlEqualTo(PROOF_URL)).withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("a content type with parameters is reduced to the bare type")
    void normalizesTheContentType() {
        stubDetailWithProof(PROOF_URL);
        stubProof(200, "Image/PNG; q=0.9", BYTES);

        assertThat(adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID).contentType()).isEqualTo("image/png");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"text/html", "image/svg+xml", "image/gif", "application/octet-stream", "not a type"})
    @DisplayName("a type outside the allowed ones is unavailable")
    void rejectsDisallowedTypes(String contentType) {
        stubDetailWithProof(PROOF_URL);
        stubProof(200, contentType, BYTES);

        assertThatThrownBy(() -> adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a proof without a content type is unavailable")
    void rejectsMissingType() {
        stubDetailWithProof(PROOF_URL);
        stubProof(200, null, BYTES);

        assertThatThrownBy(() -> adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a payment without proofUrl is PROOF_NOT_FOUND, and the proof is never asked for")
    void paymentWithoutProof() {
        stubDetail("null");

        assertThatThrownBy(() -> adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID))
                .isInstanceOfSatisfying(PaymentRefusalException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.PROOF_NOT_FOUND));
        verifyNoProofCall();
    }

    @Test
    @DisplayName("a payment the detail does not find is PAYMENT_NOT_FOUND")
    void paymentNotFound() {
        stubFor(get(urlPathEqualTo(DETAIL_PATH)).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json").withBody("{\"code\":\"PAYMENT_NOT_FOUND\"}")));

        assertThatThrownBy(() -> adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID))
                .isInstanceOfSatisfying(PaymentRefusalException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.PAYMENT_NOT_FOUND));
    }

    static Stream<Arguments> unexpectedLinks() {
        String other = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
        return Stream.of(
                Arguments.of("absolute", "http://localhost:1" + PROOF_URL),
                Arguments.of("other host", "https://evil.example" + PROOF_URL),
                Arguments.of("scheme-relative", "//evil.example" + PROOF_URL),
                Arguments.of("other payment", "/api/v1/billing/payments/" + other + "/proof?token=abc"),
                Arguments.of("dot segments", "/api/v1/billing/payments/" + PAYMENT_ID + "/../" + other
                        + "/proof?token=abc"),
                Arguments.of("other endpoint", "/api/v1/admin/billing/payments/" + PAYMENT_ID + "?token=abc"),
                Arguments.of("no token", PROOF_PATH),
                Arguments.of("empty token", PROOF_PATH + "?token="),
                Arguments.of("extra parameter", PROOF_URL + "&redirect=http://evil.example"),
                Arguments.of("encoded characters", PROOF_PATH + "?token=%2e%2e%2f"),
                Arguments.of("fragment", PROOF_URL + "#x"),
                Arguments.of("blank", " "));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unexpectedLinks")
    @DisplayName("a link that is not the expected relative one is unavailable, and it is never followed")
    void neverFollowsAnUnexpectedLink(String name, String proofUrl) {
        stubDetailWithProof(proofUrl);

        assertThatThrownBy(() -> adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyNoProofCall();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {301, 302, 401, 403, 404, 429, 500, 503})
    @DisplayName("any other status of the proof call is unavailable")
    void otherStatusesAreUnavailable(int status) {
        stubDetailWithProof(PROOF_URL);
        stubProof(status, "application/json", "{\"code\":\"INVALID_PROOF_TOKEN\"}".getBytes());

        assertThatThrownBy(() -> adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a proof of 5 MB, the API's maximum, is read whole")
    void readsFiveMegabytes() {
        byte[] max = new byte[5 * MB];
        max[max.length - 1] = 7;
        stubDetailWithProof(PROOF_URL);
        stubProof(200, "application/pdf", max);

        assertThat(adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID).content()).isEqualTo(max);
    }

    @Test
    @DisplayName("a proof over the memory limit is unavailable, and nothing of it is logged")
    void rejectsAnOversizedProof(CapturedOutput output) {
        stubDetailWithProof(PROOF_URL);
        stubProof(200, "application/pdf", new byte[7 * MB]);

        assertThatThrownBy(() -> adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(output.getAll()).contains("DataBufferLimitException")
                .doesNotContain(SIGNED_TOKEN).doesNotContain(ACCESS_TOKEN).doesNotContain(PAYMENT_ID);
    }

    @Test
    @DisplayName("a transport error on the proof call is unavailable, and nothing of it is logged")
    void transportErrorIsUnavailable(CapturedOutput output) {
        stubDetailWithProof(PROOF_URL);
        stubFor(get(urlPathEqualTo(PROOF_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter.fetchPaymentProof(ACCESS_TOKEN, PAYMENT_ID))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(output.getAll()).contains("Billing API get payment proof call failed")
                .doesNotContain(SIGNED_TOKEN).doesNotContain(ACCESS_TOKEN).doesNotContain(PAYMENT_ID);
    }
}
