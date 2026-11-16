package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The student's history page (#363): subscriptions and payments, through the rendered HTML against a
 * WireMock-stubbed Billing API. The two sections resolve independently.
 */
@DisplayName("History page integration tests")
class HistoryPageIntegrationTest extends BaseIntegrationTest {

    private static final String PAGE = "/dashboard/history";
    private static final String HISTORY = "/api/v1/billing/subscriptions/me/history";
    private static final String PAYMENTS = "/api/v1/billing/payments/me";
    private static final String PLANS = "/api/v1/billing/plans";
    private static final String SUBSCRIPTIONS_HEADING = "<h2 id=\"subscription-history-heading\">Suscripciones</h2>";
    private static final String PAYMENTS_HEADING = "<h2 id=\"payment-history-heading\">Pagos</h2>";

    private static final String PLANS_BODY =
            """
            {"plans": [
              {"id": "plan-1", "name": "Plan Mensual", "description": "Un mes", "price": 4999.00,
               "currency": "ARS", "durationDays": 30, "featured": false}]}
            """;

    private static final String HISTORY_BODY =
            """
            [{"id": "s-2", "planId": "plan-1", "status": "ACTIVE", "startDate": "2026-11-01T12:00:00Z",
              "endDate": "2026-12-01T12:00:00Z", "type": "TRIAL"},
             {"id": "s-1", "planId": "plan-1", "status": "CANCELLED", "startDate": "2026-09-01T12:00:00Z",
              "endDate": "2026-10-01T12:00:00Z", "type": "PAID"}]
            """;

    private static final String PAYMENTS_BODY =
            """
            [{"paymentId": "pay-2", "status": "AWAITING_MANUAL_VERIFICATION", "amount": 12500.50, "currency": "ARS",
              "paymentMethod": "BANK_TRANSFER", "createdAt": "2026-11-05T15:00:00Z", "providerRef": "SECRET-REF",
              "item": {"type": "PHYSICAL_COURSE", "id": "course-1", "name": null}},
             {"paymentId": "pay-1", "status": "COMPLETED", "amount": 4999.00, "currency": "ARS",
              "paymentMethod": "MERCADO_PAGO", "createdAt": "2026-09-01T15:00:00Z",
              "item": {"type": "SUBSCRIPTION", "id": "plan-1", "name": "Plan Mensual"}}]
            """;

    private static void stubJson(String url, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(url)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", status < 300 ? "application/json" : "application/problem+json")
                .withBody(body)));
    }

    private static void stubAll() {
        stubJson(PLANS, 200, PLANS_BODY);
        stubJson(HISTORY, 200, HISTORY_BODY);
        stubJson(PAYMENTS, 200, PAYMENTS_BODY);
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

    private String studentPage() throws Exception {
        String page = mockMvc.perform(get(PAGE).cookie(logIn("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        return HtmlSections.mainOf(page);
    }

    @Test
    @DisplayName("a student sees both sections, newest first, with Spanish statuses and dates in the display zone")
    void bothSections() throws Exception {
        stubAll();

        String main = studentPage();

        assertThat(main).contains(SUBSCRIPTIONS_HEADING).contains(PAYMENTS_HEADING)
                .contains("<caption").contains("scope=\"col\"")
                .contains("Plan Mensual").contains("Activa").contains("Cancelada")
                .contains("01/11/2026").contains("01/12/2026").contains("01/09/2026").contains("01/10/2026")
                .contains("Transferencia bancaria").contains("Mercado Pago")
                .contains("En verificación").contains("Pagado")
                .contains("05/11/2026").contains("12.500,50").contains("4.999,00")
                .doesNotContain("Todavía no tenés");
        assertThat(main.indexOf("En verificación")).isLessThan(main.indexOf("Pagado"));
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(HISTORY))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("STUDENT"))));
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(PAYMENTS))
                .withQueryParam("limit", equalTo("20"))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("STUDENT"))));
    }

    @Test
    @DisplayName("a trial is flagged \"En prueba\" and a physical course without a name reads \"Curso presencial\"")
    void trialAndPhysicalCourse() throws Exception {
        stubAll();

        String main = studentPage();

        assertThat(main).containsPattern("<span class=\"badge badge-neutral\">En prueba</span>");
        assertThat(main.split("En prueba", -1)).hasSize(2);
        assertThat(main).contains("Curso presencial");
    }

    @Test
    @DisplayName("neither the raw JSON, nor fields the page does not show, nor the token reach the HTML")
    void leaksNothing() throws Exception {
        stubAll();

        String page = mockMvc.perform(get(PAGE).cookie(logIn("STUDENT"))).andReturn().getResponse()
                .getContentAsString();

        assertThat(page).doesNotContain("SECRET-REF").doesNotContain("providerRef").doesNotContain("pay-1")
                .doesNotContain("paymentId").doesNotContain("AWAITING_MANUAL_VERIFICATION")
                .doesNotContain("MERCADO_PAGO").doesNotContain("\"planId\"")
                .doesNotContain(TestAccessTokens.withRole("STUDENT")).doesNotContain("refresh-token-abc");
    }

    @Test
    @DisplayName("text coming from the API is escaped, never rendered as markup")
    void escapesApiText() throws Exception {
        stubJson(PLANS, 200, PLANS_BODY.replace("Plan Mensual", "<script>alert(1)</script>"));
        stubJson(HISTORY, 200, HISTORY_BODY);
        stubJson(PAYMENTS, 200, PAYMENTS_BODY.replace("Plan Mensual", "<img src=x onerror=alert(2)>"));

        String main = studentPage();

        assertThat(main).doesNotContain("<script>alert(1)</script>").doesNotContain("<img src=x")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;").contains("&lt;img src=x onerror=alert(2)&gt;");
    }

    @Test
    @DisplayName("a student with no history sees both empty states")
    void emptyStates() throws Exception {
        stubJson(HISTORY, 200, "[]");
        stubJson(PAYMENTS, 200, "[]");

        String main = studentPage();

        assertThat(main).contains("Todavía no tenés suscripciones.").contains("Todavía no tenés pagos.")
                .doesNotContain("<table");
    }

    @Test
    @DisplayName("a payments failure shows a status notice there and keeps the subscriptions")
    void paymentsUnavailable() throws Exception {
        stubJson(PLANS, 200, PLANS_BODY);
        stubJson(HISTORY, 200, HISTORY_BODY);
        stubJson(PAYMENTS, 500, "{\"status\":500,\"code\":\"INTERNAL_ERROR\"}");

        String main = studentPage();

        assertThat(main).contains(PAYMENTS_HEADING)
                .containsPattern("role=\"status\"[^>]*>\\s*<p>No pudimos cargar tus pagos en este momento\\. "
                        + "Probá de nuevo en unos minutos\\.</p>")
                .doesNotContain("INTERNAL_ERROR").doesNotContain("role=\"alert\"")
                .contains("Cancelada").contains("En prueba").doesNotContain("Todavía no tenés suscripciones.");
    }

    @Test
    @DisplayName("a subscriptions failure shows a status notice there and keeps the payments")
    void subscriptionsUnavailable() throws Exception {
        stubJson(HISTORY, 503, "{\"status\":503}");
        stubJson(PAYMENTS, 200, PAYMENTS_BODY);

        String main = studentPage();

        assertThat(main).contains(SUBSCRIPTIONS_HEADING)
                .containsPattern("role=\"status\"[^>]*>\\s*<p>No pudimos cargar tus suscripciones en este "
                        + "momento\\. Probá de nuevo en unos minutos\\.</p>")
                .contains("Curso presencial").contains("Pagado").doesNotContain("Todavía no tenés pagos.");
    }

    @Test
    @DisplayName("a catalog failure only degrades the plan names of the subscriptions")
    void catalogUnavailable() throws Exception {
        stubJson(PLANS, 503, "{\"status\":503}");
        stubJson(HISTORY, 200, HISTORY_BODY);
        stubJson(PAYMENTS, 200, "[]");

        String main = studentPage();

        assertThat(main).contains("Tu plan").contains("Cancelada")
                .doesNotContain("No pudimos cargar tus suscripciones");
    }

    @Test
    @DisplayName("a status this BFF does not know is shown as unknown, not as a failure")
    void unknownStatus() throws Exception {
        stubJson(PLANS, 200, PLANS_BODY);
        stubJson(HISTORY, 200, HISTORY_BODY.replace("CANCELLED", "SUSPENDED"));
        stubJson(PAYMENTS, 200, PAYMENTS_BODY.replace("COMPLETED", "REFUNDED"));

        String main = studentPage();

        assertThat(main).contains("Estado desconocido").doesNotContain("SUSPENDED").doesNotContain("REFUNDED")
                .doesNotContain("No pudimos cargar");
    }

    @ParameterizedTest(name = "a {0} gets a 403 and no Billing call")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR", "RECEPTIONIST"})
    void staffIsForbidden(String role) throws Exception {
        stubAll();
        Cookie session = logIn(role);

        mockMvc.perform(get(PAGE).cookie(session)).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathEqualTo(HISTORY)));
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathEqualTo(PAYMENTS)));
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login and no Billing call is made")
    void anonymousGoesToLogin() throws Exception {
        stubAll();

        mockMvc.perform(get(PAGE)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));

        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathEqualTo(HISTORY)));
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathEqualTo(PAYMENTS)));
    }

    @Test
    @DisplayName("the student's dashboard links to the history page")
    void dashboardLinksToHistory() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo("/api/v1/billing/subscriptions/me")).willReturn(
                aResponse().withStatus(404).withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"status\":404,\"code\":\"NO_SUBSCRIPTION\"}")));
        stubJson("/api/v1/virtual/courses/me/in-progress", 200, "[]");

        String main = HtmlSections.mainOf(mockMvc.perform(get("/dashboard").cookie(logIn("STUDENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(main).contains("href=\"/dashboard/history\"").contains("Ver historial");
    }
}
