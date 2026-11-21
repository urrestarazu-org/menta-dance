package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The transfer proof of a payment, {@code GET /gestion/pagos/{paymentId}/comprobante} (#370): who may open it, the
 * bytes and headers the administrator gets, and what the browser, the cache and the logs never see of the API.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Management payment proof Integration Tests")
class ManagementPaymentProofIntegrationTest extends BaseIntegrationTest {

    private static final String PAYMENT_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String PATH = "/gestion/pagos/" + PAYMENT_ID + "/comprobante";
    private static final String DETAIL_API = "/api/v1/admin/billing/payments/" + PAYMENT_ID;
    private static final String PROOF_API = "/api/v1/billing/payments/" + PAYMENT_ID + "/proof";
    private static final String SIGNED_TOKEN = "fake-proof_one.fake-proof_two";
    private static final byte[] BYTES = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2};

    private Cookie logIn(String role) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(TestAccessTokens.withRole(role)))));
        Cookie session = mockMvc.perform(post("/login").param("email", "user@example.com")
                        .param("password", "Password1").with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private static void stubDetail(int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(DETAIL_API)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static String detailWith(String proofUrlJson) {
        return ("{\"paymentId\":\"%s\",\"userId\":\"u-1\",\"targetModality\":\"VIRTUAL\",\"targetReference\":\"p\","
                + "\"amount\":9000,\"currency\":\"ARS\",\"statusType\":\"AWAITING_MANUAL_VERIFICATION\","
                + "\"createdAt\":\"2026-11-20T15:30:00Z\",\"proofUrl\":%s}").formatted(PAYMENT_ID, proofUrlJson);
    }

    private static void stubProof(int status, String contentType, byte[] body) {
        stubDetail(200, detailWith("\"" + PROOF_API + "?token=" + SIGNED_TOKEN + "\""));
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(PROOF_API + "?token=" + SIGNED_TOKEN))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", contentType).withBody(body)));
    }

    private MockHttpServletResponse adminRequest(String path) throws Exception {
        return mockMvc.perform(get(path).cookie(logIn("ADMIN"))).andReturn().getResponse();
    }

    private static void assertEmptyAndNotCached(MockHttpServletResponse response, int status) {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"image/png", "image/jpeg", "application/pdf"})
    @DisplayName("an administrator gets the bytes with the declared type and every protective header")
    void adminGetsTheProof(String contentType) throws Exception {
        stubProof(200, contentType, BYTES);

        MockHttpServletResponse response = adminRequest(PATH);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo(BYTES);
        assertThat(response.getContentType()).isEqualTo(contentType);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("inline");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        // The browser is shown nothing of the API: no link, no token.
        assertThat(response.getHeaderNames()).noneMatch(name -> name.equalsIgnoreCase("Location"));
        assertThat(response.getHeaders("Content-Disposition")).noneMatch(value -> value.contains(SIGNED_TOKEN));
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlEqualTo(DETAIL_API))
                .withHeader("Authorization", matching("Bearer .+")));
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlEqualTo(PROOF_API + "?token=" + SIGNED_TOKEN))
                .withoutHeader("Authorization"));
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"INSTRUCTOR", "RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        stubProof(200, "image/png", BYTES);

        mockMvc.perform(get(PATH).cookie(logIn(role))).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/(admin/)?billing/.*")));
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/(admin/)?billing/.*")));
    }

    static Stream<Arguments> notFoundCases() {
        return Stream.of(
                Arguments.of("payment unknown to the API", 404, "{\"code\":\"PAYMENT_NOT_FOUND\"}"),
                Arguments.of("payment without proof", 200, detailWith("null")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notFoundCases")
    @DisplayName("a payment or proof that does not exist is a 404, without body and not cached")
    void notFound(String name, int detailStatus, String detailBody) throws Exception {
        stubDetail(detailStatus, detailBody);

        assertEmptyAndNotCached(adminRequest(PATH), 404);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"not-a-uuid", "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5"})
    @DisplayName("an id that is not a UUID is a 404, without asking the API")
    void idThatIsNotAUuid(String id) throws Exception {
        assertEmptyAndNotCached(adminRequest("/gestion/pagos/" + id + "/comprobante"), 404);

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/(admin/)?billing/.*")));
    }

    static Stream<Arguments> unavailableCases() {
        return Stream.of(
                Arguments.of("detail fails", 500, "{\"detail\":\"secret-detail\"}", 200),
                Arguments.of("proof token refused", 200,
                        detailWith("\"" + PROOF_API + "?token=" + SIGNED_TOKEN + "\""), 403),
                Arguments.of("link to another host", 200,
                        detailWith("\"https://evil.example" + PROOF_API + "?token=" + SIGNED_TOKEN + "\""), 200));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailableCases")
    @DisplayName("any other failure is a 503 without body, not cached, and nothing of the API in the logs")
    void unavailable(String name, int detailStatus, String detailBody, int proofStatus, CapturedOutput output)
            throws Exception {
        stubDetail(detailStatus, detailBody);
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(PROOF_API + "?token=" + SIGNED_TOKEN)).willReturn(aResponse()
                .withStatus(proofStatus).withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"INVALID_PROOF_TOKEN\",\"detail\":\"secret-detail\"}")));

        assertEmptyAndNotCached(adminRequest(PATH), 503);
        assertThat(output.getAll()).doesNotContain(SIGNED_TOKEN).doesNotContain("secret-detail")
                .doesNotContain("evil.example");
    }
}
