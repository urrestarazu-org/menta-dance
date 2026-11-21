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
 * The administrator's screen of purchases in exception (#370): the list {@code GET /gestion/compras-con-excepcion}
 * and the resolution of each row. Each case logs in for real against the stubbed APIs and reuses the session cookie,
 * so the flash messages of Post/Redirect/Get are exercised too.
 */
@DisplayName("Management purchases in exception Integration Tests")
class ManagementExceptionPurchasesIntegrationTest extends BaseIntegrationTest {

    private static final String ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String OTHER_ID = "6c7d8e9f-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String USER_ID = "9f8e7d6c-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String LIST = "/gestion/compras-con-excepcion";
    private static final String RESOLVE = LIST + "/" + ID + "/resolver";
    private static final String API = "/api/v1/admin/billing/purchases";
    private static final String RESOLVE_API = API + "/" + ID + "/resolution";
    private static final String SECRET = "secret-detail";
    private static final String UNAVAILABLE = "No pudimos comunicarnos con el servicio de pagos. Intentá de nuevo.";

    private static String item(String id, int sessions) {
        String sessionIds = sessions == 0 ? "" : "\"s-1\"" + ",\"s-2\"".repeat(sessions - 1);
        return ("{\"purchaseId\":\"%s\",\"userId\":\"%s\",\"targetModality\":\"PHYSICAL\",\"amount\":25000,"
                + "\"currency\":\"ARS\",\"createdAt\":\"2026-11-21T02:30:00Z\",\"physicalSessionIds\":[%s]}")
                .formatted(id, USER_ID, sessionIds);
    }

    private static void stubList(int page, int size, int totalPages, String... items) {
        stub(WireMock.get(urlPathEqualTo(API)), 200,
                "{\"items\":[%s],\"page\":%d,\"size\":%d,\"totalElements\":%d,\"totalPages\":%d}"
                        .formatted(String.join(",", items), page, size, items.length * totalPages, totalPages));
    }

    private static void stub(com.github.tomakehurst.wiremock.client.MappingBuilder mapping, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(mapping.willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static void stubResolve(int status, String code) {
        stub(WireMock.post(urlEqualTo(RESOLVE_API)), status,
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

    private static void verifyNoBillingCall() {
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    // ---- the list ----

    @Test
    @DisplayName("lists the purchases in exception in an accessible table, worded for the screen, never cached")
    void listsPurchases() throws Exception {
        stubList(0, 20, 1, item(ID, 2), item(OTHER_ID, 0));

        MockHttpServletResponse response = page(logIn("ADMIN"), LIST);
        String main = mainOf(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(main).containsPattern("<caption[^>]*>\\s*Compras con excepción")
                .containsPattern("<th[^>]*scope=\"col\"[^>]*>\\s*Sesiones afectadas\\s*</th>")
                .contains("9f8e7d6c").doesNotContain(USER_ID).contains("25.000,00 ARS", "Presencial")
                .contains("20 de noviembre de 2026, 23:30").contains("En excepción")
                .containsPattern("<td[^>]*>2</td>").containsPattern("<td[^>]*>0</td>")
                .contains("action=\"" + RESOLVE + "?page=0&amp;size=20\"", "Reembolsada", "Compensada")
                .containsPattern("<summary>\\s*Resolver").contains("Resolver compra").contains("maxlength=\"500\"")
                .doesNotContain("/api/v1").doesNotContain("<details open");
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlPathEqualTo(API)).withQueryParam("status", equalTo("EXCEPTION"))
                .withQueryParam("page", equalTo("0")).withQueryParam("size", equalTo("20"))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN"))));
    }

    @Test
    @DisplayName("each row's fields have their own ids, so labels and descriptions point at the right control")
    void fieldIdsAreUniquePerRow() throws Exception {
        stubList(0, 20, 1, item(ID, 1), item(OTHER_ID, 1));

        String main = mainOf(page(logIn("ADMIN"), LIST).getContentAsString());

        assertThat(main).contains("id=\"resolve-note-0\"", "id=\"resolve-note-1\"", "for=\"resolve-note-1\"",
                "id=\"resolve-resolution-0\"", "id=\"resolve-resolution-1\"");
    }

    @Test
    @DisplayName("an empty list says so in Spanish, without a table")
    void emptyList() throws Exception {
        stubList(0, 20, 0);

        String main = mainOf(page(logIn("ADMIN"), LIST).getContentAsString());

        assertThat(main).contains("No hay compras con excepción para resolver.").doesNotContain("<table");
    }

    @Test
    @DisplayName("pages with Anterior and Siguiente, keeping the page size, and asks the API for what the query says")
    void paginates() throws Exception {
        stubList(1, 10, 3, item(ID, 1));

        String main = mainOf(page(logIn("ADMIN"), LIST, "page", "1", "size", "10").getContentAsString());

        assertThat(main).contains("Página 2 de 3")
                .containsPattern("<a[^>]*rel=\"prev\"[^>]*href=\"" + LIST + "\\?page=0&amp;size=10\"")
                .containsPattern("<a[^>]*rel=\"next\"[^>]*href=\"" + LIST + "\\?page=2&amp;size=10\"");
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlPathEqualTo(API)).withQueryParam("page", equalTo("1"))
                .withQueryParam("size", equalTo("10")));
    }

    @Test
    @DisplayName("when the API is down the list shows a notice with a 503 and nothing of the API's answer")
    void listUnavailable() throws Exception {
        stub(WireMock.get(urlPathEqualTo(API)), 500, "{\"detail\":\"" + SECRET + "\"}");

        MockHttpServletResponse response = page(logIn("ADMIN"), LIST);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(mainOf(response.getContentAsString())).contains(UNAVAILABLE).doesNotContain(SECRET)
                .doesNotContain("<table");
    }

    // ---- the resolution ----

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"REFUNDED", "COMPENSATED"})
    @DisplayName("resolving posts the decision and the trimmed note, goes back to the list and confirms once")
    void resolves(String resolution) throws Exception {
        stubResolve(200, null);
        stubList(0, 20, 1);
        Cookie session = logIn("ADMIN");

        MockHttpServletResponse response = mockMvc.perform(action(session, RESOLVE, "resolution", resolution,
                        "note", "  Se devolvió por transferencia "))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST)).andReturn().getResponse();

        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(RESOLVE_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN")))
                .withRequestBody(equalToJson("{\"resolution\":\"%s\",\"note\":\"Se devolvió por transferencia\"}"
                        .formatted(resolution))));
        assertThat(mainOf(page(session, LIST).getContentAsString())).containsPattern(
                "<[^>]*role=\"status\"[^>]*>\\s*<p>Compra resuelta.</p>");
        assertThat(mainOf(page(session, LIST).getContentAsString())).doesNotContain("Compra resuelta.");
    }

    static Stream<Arguments> invalidSubmissions() {
        return Stream.of(
                Arguments.of(new String[] {"resolution", "REFUNDED", "note", "   "},
                        "Ingresá la nota de la resolución"),
                Arguments.of(new String[] {"resolution", "COMPENSATED", "note", "x".repeat(501)},
                        "La nota no puede superar los 500 caracteres"),
                Arguments.of(new String[] {"note", "Nota"}, "Elegí una resolución"),
                Arguments.of(new String[] {"resolution", "MAYBE", "note", "Nota"}, "Elegí una resolución"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidSubmissions")
    @DisplayName("invalid input re-renders the list with the error in that row, opened, without calling the API")
    void validationErrors(String[] params, String message) throws Exception {
        stubList(0, 20, 1, item(OTHER_ID, 1), item(ID, 1));

        MockHttpServletResponse response = mockMvc.perform(action(logIn("ADMIN"), RESOLVE, params))
                .andReturn().getResponse();
        String main = mainOf(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(main).contains("data-error-summary").containsPattern("<p[^>]*class=\"field-error\"[^>]*>" + message)
                .contains("aria-invalid=\"true\"").containsOnlyOnce("<details open")
                .containsPattern("<details open[^>]*>(?s:(?!</details>).)*id=\"resolve-note-1\"");
        assertThat(main.split("class=\"field-error\"", -1)).hasSize(2);
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathMatching(API + "/.*")));
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(409, "PURCHASE_ALREADY_RESOLVED", "La compra ya fue resuelta con otra decisión o nota."),
                Arguments.of(409, "ILLEGAL_PURCHASE_STATE_TRANSITION", "La compra ya no está en excepción."),
                Arguments.of(404, "PURCHASE_NOT_FOUND", "No encontramos la compra."),
                Arguments.of(400, "INVALID_REQUEST", "Revisá los datos ingresados."),
                Arguments.of(500, "BOOM", UNAVAILABLE));
    }

    @ParameterizedTest(name = "API {0} {1}")
    @MethodSource("refusals")
    @DisplayName("each refusal of the API is worded in Spanish from its code, shown once after the redirect, "
            + "and nothing of its body reaches the page")
    void refusalsAreWorded(int apiStatus, String code, String message) throws Exception {
        stubResolve(apiStatus, code);
        stubList(0, 20, 1, item(ID, 1));
        Cookie session = logIn("ADMIN");

        mockMvc.perform(action(session, RESOLVE, "resolution", "REFUNDED", "note", "Nota"))
                .andExpect(redirectedUrl(LIST));

        String main = mainOf(page(session, LIST).getContentAsString());
        assertThat(main).containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>" + message + "</p>")
                .doesNotContain(SECRET).doesNotContain(code);
        assertThat(mainOf(page(session, LIST).getContentAsString())).doesNotContain(message);
    }

    @Test
    @DisplayName("an id that is not a UUID is answered as a missing purchase, without asking the API")
    void invalidId() throws Exception {
        Cookie session = logIn("ADMIN");

        mockMvc.perform(action(session, LIST + "/not-a-uuid/resolver", "resolution", "REFUNDED", "note", "Nota"))
                .andExpect(redirectedUrl(LIST));

        verifyNoBillingCall();
    }

    // ---- access ----

    @Test
    @DisplayName("resolving requires the CSRF token: without it the answer is a 403 and the API is not called")
    void csrfIsRequired() throws Exception {
        mockMvc.perform(post(RESOLVE).cookie(logIn("ADMIN")).param("resolution", "REFUNDED").param("note", "x"))
                .andExpect(status().isForbidden());

        verifyNoBillingCall();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"INSTRUCTOR", "RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on both routes, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);

        mockMvc.perform(get(LIST).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(action(session, RESOLVE, "resolution", "REFUNDED", "note", "x"))
                .andExpect(status().isForbidden());
        verifyNoBillingCall();
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page on the list and on the resolution")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(LIST)).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(RESOLVE).with(csrf())).andExpect(redirectedUrlPattern("**/login"));

        verifyNoBillingCall();
    }

    @Test
    @DisplayName("the administrator's navigation offers the section, marked as the current one on its screen")
    void navigationOffersTheSection() throws Exception {
        stubList(0, 20, 0);

        String main = page(logIn("ADMIN"), LIST).getContentAsString();

        assertThat(main).containsPattern(
                "<a[^>]*href=\"" + LIST + "\"[^>]*aria-current=\"page\"[^>]*>Compras con excepción</a>");
    }
}
