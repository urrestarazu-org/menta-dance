package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The administrator's screens to verify manual payments (#370): the inbox {@code GET /gestion/pagos}, the detail
 * {@code GET /gestion/pagos/{id}} and the three actions on it. Each case logs in for real against the stubbed
 * APIs and reuses the session cookie, so the flash messages of Post/Redirect/Get are exercised too.
 */
@DisplayName("Management payments Integration Tests")
class ManagementPaymentsIntegrationTest extends BaseIntegrationTest {

    private static final String ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String USER_ID = "9f8e7d6c-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String LIST = "/gestion/pagos";
    private static final String DETAIL = LIST + "/" + ID;
    private static final String API = "/api/v1/admin/billing/payments";
    private static final String DETAIL_API = API + "/" + ID;
    private static final String APPROVE_API = DETAIL_API + "/approve";
    private static final String REJECT_API = DETAIL_API + "/reject";
    private static final String CORRECT_API = DETAIL_API + "/corrections";
    private static final String AWAITING = "AWAITING_MANUAL_VERIFICATION";
    private static final String RECONCILIATION = "RECONCILIATION_REQUIRED";
    private static final String SECRET = "secret-detail";
    private static final String TOO_LONG = "x".repeat(501);
    private static final String ILLEGAL =
            "El pago ya fue resuelto o ya no admite esta operación. Revisá su estado actual.";
    private static final String UNAVAILABLE = "No pudimos comunicarnos con el servicio de pagos. Intentá de nuevo.";

    /** One payment as the list and the detail of the API word it: {@code hasProof} for the first, {@code proofUrl}. */
    private static String item(String status, String modality, boolean proof) {
        return ("{\"paymentId\":\"%s\",\"userId\":\"%s\",\"targetModality\":\"%s\",\"targetReference\":\"c-1\","
                + "\"amount\":25000,\"currency\":\"ARS\",\"statusType\":\"%s\",\"hasProof\":%b,"
                + "\"createdAt\":\"2026-11-21T02:30:00Z\",\"proofUrl\":%s}").formatted(ID, USER_ID, modality, status,
                proof, proof ? "\"/api/v1/billing/payments/" + ID + "/proof?token=fake-proof_one\"" : "null");
    }

    private static void stubList(int page, int size, int totalPages, String... items) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(API)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"items\":[%s],\"page\":%d,\"size\":%d,\"totalElements\":%d,\"totalPages\":%d}"
                        .formatted(String.join(",", items), page, size, items.length * totalPages, totalPages))));
    }

    private static void stubDetail(String status, boolean proof) {
        stub(WireMock.get(urlEqualTo(DETAIL_API)), 200, item(status, "PHYSICAL", proof));
    }

    private static void stub(com.github.tomakehurst.wiremock.client.MappingBuilder mapping, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(mapping.willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static void stubAction(String path, int status, String code) {
        stub(WireMock.post(urlEqualTo(path)), status,
                code == null ? "" : "{\"code\":\"%s\",\"detail\":\"%s\"}".formatted(code, SECRET));
    }

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

    private MockHttpServletResponse page(Cookie session, String path, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(path).cookie(session);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder action(Cookie session, String path, String... params) {
        MockHttpServletRequestBuilder request = post(path).cookie(session).with(csrf());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return request;
    }

    private static void verifyNoPaymentCall() {
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    private static void verifyNoPostToApi() {
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathMatching(API + "/.*")));
    }

    // ---- the inbox ----

    @Test
    @DisplayName("lists the pending payments in an accessible table, worded for the screen, never cached")
    void listsPayments() throws Exception {
        stubList(0, 20, 1, item(AWAITING, "PHYSICAL", true), item(AWAITING, "UNKNOWN", false));

        MockHttpServletResponse response = page(logIn("ADMIN"), LIST);
        String main = mainOf(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(main).containsPattern("<caption[^>]*>\\s*Pagos pendientes de verificación")
                .containsPattern("<th[^>]*scope=\"col\"[^>]*>\\s*Alumno\\s*</th>")
                .contains("9f8e7d6c").doesNotContain(USER_ID)
                .contains("25.000,00 ARS").contains("20 de noviembre de 2026, 23:30")
                .contains("Transferencia").contains("Presencial").contains("Sin dato")
                .containsPattern("<a[^>]*href=\"" + DETAIL + "\"[^>]*>\\s*Ver\\s*<span class=\"sr-only\"")
                .doesNotContain("proofUrl").doesNotContain("token=");
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlPathEqualTo(API)).withQueryParam("page", equalTo("0"))
                .withQueryParam("size", equalTo("20")).withHeader("Authorization", equalTo(
                        "Bearer " + TestAccessTokens.withRole("ADMIN"))));
    }

    @Test
    @DisplayName("an empty inbox says so in Spanish, without a table")
    void emptyInbox() throws Exception {
        stubList(0, 20, 0);

        String main = mainOf(page(logIn("ADMIN"), LIST).getContentAsString());

        assertThat(main).contains("No hay pagos pendientes de verificación.").doesNotContain("<table");
    }

    @Test
    @DisplayName("pages with Anterior and Siguiente, keeping the page size, and asks the API for what the query says")
    void paginates() throws Exception {
        stubList(1, 10, 3, item(AWAITING, "VIRTUAL", false));

        String main = mainOf(page(logIn("ADMIN"), LIST, "page", "1", "size", "10").getContentAsString());

        assertThat(main).contains("Página 2 de 3")
                .containsPattern("<a[^>]*rel=\"prev\"[^>]*href=\"" + LIST + "\\?page=0&amp;size=10\"")
                .containsPattern("<a[^>]*rel=\"next\"[^>]*href=\"" + LIST + "\\?page=2&amp;size=10\"");
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlPathEqualTo(API)).withQueryParam("page", equalTo("1"))
                .withQueryParam("size", equalTo("10")));
    }

    @Test
    @DisplayName("when the API is down the inbox shows a notice with a 503 and nothing of the API's answer")
    void inboxUnavailable() throws Exception {
        stub(WireMock.get(urlPathEqualTo(API)), 500, "{\"detail\":\"" + SECRET + "\"}");

        MockHttpServletResponse response = page(logIn("ADMIN"), LIST);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(mainOf(response.getContentAsString())).contains(UNAVAILABLE).doesNotContain(SECRET)
                .doesNotContain("<table");
    }

    // ---- the detail ----

    static Stream<Arguments> detailCases() {
        String approve = "action=\"" + DETAIL + "/aprobar\"";
        String reject = "action=\"" + DETAIL + "/rechazar\"";
        String correct = "action=\"" + DETAIL + "/corregir\"";
        return Stream.of(
                Arguments.of(AWAITING, true, List.of(approve, reject, "Pendiente de verificación", "maxlength=\"500\"",
                        "target=\"_blank\"", "rel=\"noopener\"", "href=\"" + DETAIL + "/comprobante\""),
                        List.of(correct, "no tiene comprobante")),
                Arguments.of(AWAITING, false, List.of(approve, reject, "Este pago no tiene comprobante"),
                        List.of(correct, "Ver comprobante")),
                Arguments.of(RECONCILIATION, true, List.of(correct, "Requiere conciliación", "name=\"decision\"",
                        "name=\"evidence\""), List.of(approve, reject)),
                Arguments.of("COMPLETED", true, List.of("Aprobado", "Ver comprobante"),
                        List.of(approve, reject, correct, "<form")));
    }

    @ParameterizedTest(name = "{0}, proof={1}")
    @MethodSource("detailCases")
    @DisplayName("the detail offers only what the status admits, and never exposes the API's signed link")
    void detailByStatus(String status, boolean proof, List<String> present, List<String> absent) throws Exception {
        stubDetail(status, proof);

        MockHttpServletResponse response = page(logIn("ADMIN"), DETAIL);
        String main = mainOf(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(main).contains(present).doesNotContain(absent).contains("25.000,00 ARS", "9f8e7d6c")
                .doesNotContain("proofUrl").doesNotContain("token=").doesNotContain("/api/v1");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"not-a-uuid", "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5"})
    @DisplayName("an id that is not a UUID is a 404 page back to the inbox, without asking the API")
    void invalidId(String id) throws Exception {
        MockHttpServletResponse response = page(logIn("ADMIN"), LIST + "/" + id);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(mainOf(response.getContentAsString())).contains("No encontramos el pago.")
                .containsPattern("<a[^>]*href=\"/gestion/pagos\"[^>]*>\\s*Volver a los pagos");
        verifyNoPaymentCall();
    }

    @ParameterizedTest(name = "API {0} {1} -> page {2}")
    @MethodSource("detailFailures")
    @DisplayName("a payment unknown to the API is a 404 page and any other failure a 503, with nothing of its body")
    void detailFailure(int apiStatus, String code, int pageStatus, String message) throws Exception {
        stub(WireMock.get(urlEqualTo(DETAIL_API)), apiStatus,
                "{\"code\":\"%s\",\"detail\":\"%s\"}".formatted(code, SECRET));

        MockHttpServletResponse response = page(logIn("ADMIN"), DETAIL);

        assertThat(response.getStatus()).isEqualTo(pageStatus);
        assertThat(mainOf(response.getContentAsString())).contains(message).doesNotContain(SECRET);
    }

    static Stream<Arguments> detailFailures() {
        return Stream.of(Arguments.of(404, "PAYMENT_NOT_FOUND", 404, "No encontramos el pago."),
                Arguments.of(500, "X", 503, UNAVAILABLE));
    }

    // ---- the actions ----

    @Test
    @DisplayName("approving posts to the API with the administrator's token, goes back to the inbox and confirms once")
    void approves() throws Exception {
        stubAction(APPROVE_API, 200, null);
        stubList(0, 20, 1);
        Cookie session = logIn("ADMIN");

        MockHttpServletResponse response = mockMvc.perform(action(session, DETAIL + "/aprobar"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST)).andReturn().getResponse();

        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(APPROVE_API)).withHeader("Authorization",
                equalTo("Bearer " + TestAccessTokens.withRole("ADMIN"))));
        assertThat(mainOf(page(session, LIST).getContentAsString())).containsPattern(
                "<[^>]*role=\"status\"[^>]*>\\s*<p>Pago aprobado.</p>");
        assertThat(mainOf(page(session, LIST).getContentAsString())).doesNotContain("Pago aprobado.");
    }

    @Test
    @DisplayName("rejecting sends the trimmed reason, goes back to the inbox and confirms")
    void rejects() throws Exception {
        stubAction(REJECT_API, 200, null);
        stubList(0, 20, 1);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(action(session, DETAIL + "/rechazar", "reason", "  Comprobante ilegible "))
                .andExpect(redirectedUrl(LIST));

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(REJECT_API))
                .withRequestBody(equalToJson("{\"reason\":\"Comprobante ilegible\"}")));
        assertThat(mainOf(page(session, LIST).getContentAsString())).contains("Pago rechazado.");
    }

    @Test
    @DisplayName("correcting sends decision, reason and evidence, goes back to the detail and confirms")
    void corrects() throws Exception {
        stubAction(CORRECT_API, 200, null);
        stubDetail("COMPLETED", true);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(action(session, DETAIL + "/corregir", "decision", "APPROVED", "reason", "Llegó el dinero",
                "evidence", "Extracto del 20/11")).andExpect(redirectedUrl(DETAIL));

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(CORRECT_API)).withRequestBody(equalToJson(
                "{\"decision\":\"APPROVED\",\"reason\":\"Llegó el dinero\",\"evidence\":\"Extracto del 20/11\"}")));
        assertThat(mainOf(page(session, DETAIL).getContentAsString())).contains("Corrección registrada.");
    }

    static Stream<Arguments> invalidSubmissions() {
        String long501 = TOO_LONG;
        return Stream.of(
                Arguments.of("rechazar", AWAITING, new String[] {"reason", "   "}, "Ingresá el motivo del rechazo"),
                Arguments.of("rechazar", AWAITING, new String[] {"reason", long501},
                        "El motivo no puede superar los 500 caracteres"),
                Arguments.of("corregir", RECONCILIATION,
                        new String[] {"decision", "MAYBE", "reason", "m", "evidence", "e"}, "Elegí una decisión"),
                Arguments.of("corregir", RECONCILIATION, new String[] {"decision", "REJECTED", "reason", "m"},
                        "Ingresá la evidencia"),
                Arguments.of("corregir", RECONCILIATION,
                        new String[] {"decision", "REJECTED", "reason", "m", "evidence", long501},
                        "La evidencia no puede superar los 500 caracteres"));
    }

    @ParameterizedTest(name = "{0}: {3}")
    @MethodSource("invalidSubmissions")
    @DisplayName("invalid input re-renders the detail with the error next to its field, without calling the API")
    void validationErrors(String path, String status, String[] params, String message) throws Exception {
        stubDetail(status, true);

        MockHttpServletResponse response = mockMvc.perform(action(logIn("ADMIN"), DETAIL + "/" + path, params))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(mainOf(response.getContentAsString())).contains("data-error-summary")
                .containsPattern("<p[^>]*class=\"field-error\"[^>]*>" + message).contains("aria-invalid=\"true\"");
        verifyNoPostToApi();
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(409, "ILLEGAL_PAYMENT_STATE_TRANSITION", ILLEGAL, DETAIL),
                Arguments.of(404, "PAYMENT_NOT_FOUND", "No encontramos el pago.", LIST),
                Arguments.of(400, "INVALID_REQUEST", "Revisá los datos ingresados.", DETAIL),
                Arguments.of(500, "BOOM", UNAVAILABLE, DETAIL));
    }

    @ParameterizedTest(name = "API {0} {1}")
    @MethodSource("refusals")
    @DisplayName("each refusal of the API is worded in Spanish from its code, shown once after the redirect, "
            + "and nothing of its body reaches the page")
    void refusalsAreWorded(int apiStatus, String code, String message, String target) throws Exception {
        stubAction(APPROVE_API, apiStatus, code);
        stubDetail(AWAITING, true);
        stubList(0, 20, 1);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(action(session, DETAIL + "/aprobar")).andExpect(redirectedUrl(target));

        String main = mainOf(page(session, target).getContentAsString());
        assertThat(main).containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>" + message + "</p>")
                .doesNotContain(SECRET).doesNotContain(code);
    }

    // ---- access ----

    static Stream<String> actionPaths() {
        return Stream.of(DETAIL + "/aprobar", DETAIL + "/rechazar", DETAIL + "/corregir");
    }

    @ParameterizedTest(name = "{0} without a CSRF token")
    @MethodSource("actionPaths")
    @DisplayName("every action requires the CSRF token: without it the answer is a 403 and the API is not called")
    void csrfIsRequired(String path) throws Exception {
        mockMvc.perform(post(path).cookie(logIn("ADMIN")).param("reason", "x")).andExpect(status().isForbidden());

        verifyNoPaymentCall();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"INSTRUCTOR", "RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on every route, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);

        for (String path : List.of(LIST, DETAIL)) {
            mockMvc.perform(get(path).cookie(session)).andExpect(status().isForbidden());
        }
        for (String path : actionPaths().toList()) {
            mockMvc.perform(action(session, path, "reason", "x")).andExpect(status().isForbidden());
        }
        verifyNoPaymentCall();
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page on the screens and on the actions")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(LIST)).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get(DETAIL)).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(DETAIL + "/aprobar").with(csrf())).andExpect(redirectedUrlPattern("**/login"));

        verifyNoPaymentCall();
    }
}
