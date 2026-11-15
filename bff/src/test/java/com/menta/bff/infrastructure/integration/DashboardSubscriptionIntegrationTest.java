package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The subscription card of the dashboard (#363), through the rendered HTML against a
 * WireMock-stubbed Billing API.
 */
@DisplayName("Dashboard subscription card integration tests")
class DashboardSubscriptionIntegrationTest extends BaseIntegrationTest {

    private static final String PLANS = "/api/v1/billing/plans";
    private static final String ME = "/api/v1/billing/subscriptions/me";
    private static final String CARD_HEADING = "Mi suscripción";

    private static final String PLANS_BODY =
            """
            {"plans": [
              {"id": "plan-1", "name": "Plan Mensual", "description": "Un mes", "price": 4999.00,
               "currency": "ARS", "durationDays": 30, "featured": false}]}
            """;

    private static void stubPlans() {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(PLANS)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(PLANS_BODY)));
    }

    private static void stubMe(int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(ME)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", status < 300 ? "application/json" : "application/problem+json")
                .withBody(body)));
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

    private String studentDashboard() throws Exception {
        String page = mockMvc.perform(get("/dashboard").cookie(logIn("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        return HtmlSections.mainOf(page);
    }

    private static void assertRestOfDashboard(String main) {
        assertThat(main).contains("Hola, <span>user@example.com</span>!")
                .contains("href=\"/classes\"").contains("Clases presenciales")
                .contains("action=\"/logout\"").contains("Cerrar sesión");
    }

    @Test
    @DisplayName("a student with an ACTIVE subscription sees the plan, the end date and the days left")
    void active() throws Exception {
        stubPlans();
        stubMe(200,
                """
                {"subscriptionId": "s-1", "planId": "plan-1", "status": "ACTIVE",
                "startDate": "2026-10-01T15:00:00Z", "endDate": "2026-10-31T15:00:00Z",
                "daysRemaining": 17, "expiringSoon": false, "checkoutUrl": null, "plansUrl": null}
                """);

        String main = studentDashboard();

        assertThat(main).contains("<h2", CARD_HEADING).contains("Plan Mensual")
                .contains("Activa hasta el 31/10/2026").contains("Vence en 17 días")
                .contains("href=\"/plans\"")
                .doesNotContain("Está por vencer").doesNotContain("Todavía no tenés");
        assertRestOfDashboard(main);
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlEqualTo(ME))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("STUDENT"))));
    }

    @Test
    @DisplayName("an ACTIVE subscription about to expire says so in text, not only with colour")
    void activeExpiringSoon() throws Exception {
        stubPlans();
        stubMe(200,
                """
                {"subscriptionId": "s-1", "planId": "plan-1", "status": "ACTIVE",
                "endDate": "2026-10-31T15:00:00Z", "daysRemaining": 3, "expiringSoon": true}
                """);

        assertThat(studentDashboard()).contains("Está por vencer").contains("Vence en 3 días");
    }

    @Test
    @DisplayName("a student without a subscription is pointed to the plans and the catalog is not read")
    void none() throws Exception {
        stubMe(404, "{\"status\":404,\"code\":\"NO_SUBSCRIPTION\"}");

        String main = studentDashboard();

        assertThat(main).contains(CARD_HEADING).contains("Todavía no tenés una suscripción.")
                .contains("href=\"/plans\"").doesNotContain("Activa hasta");
        assertRestOfDashboard(main);
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlEqualTo(PLANS)));
    }

    @Test
    @DisplayName("a PENDING subscription says the payment is pending and never exposes the checkout URL")
    void pending() throws Exception {
        stubPlans();
        stubMe(200,
                """
                {"subscriptionId": "0f0e0d0c-1111-2222-3333-444444444444", "planId": "plan-1",
                "status": "PENDING", "checkoutUrl": "https://pay.example.com/c/1"}
                """);

        String main = studentDashboard();

        assertThat(main).contains("Plan Mensual").contains("Tu suscripción está pendiente de pago.")
                .contains("href=\"/plans\"").doesNotContain("pay.example.com");
    }

    @Test
    @DisplayName("an EXPIRED subscription shows the date it ended and a way to subscribe again")
    void expired() throws Exception {
        stubPlans();
        stubMe(200,
                """
                {"subscriptionId": "s-1", "planId": "plan-1", "status": "EXPIRED",
                "startDate": "2026-08-01T15:00:00Z", "endDate": "2026-08-31T15:00:00Z",
                "plansUrl": "/api/v1/billing/plans"}
                """);

        String main = studentDashboard();

        assertThat(main).contains("Plan Mensual").contains("Venció el 31/08/2026")
                .contains("href=\"/plans\"").doesNotContain("href=\"/api/v1/billing/plans\"");
    }

    @Test
    @DisplayName("a plan catalog failure keeps the state with a generic plan name")
    void catalogUnavailable() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(PLANS)).willReturn(aResponse().withStatus(503)));
        stubMe(200,
                """
                {"subscriptionId": "s-1", "planId": "plan-1", "status": "ACTIVE",
                "endDate": "2026-10-31T15:00:00Z", "daysRemaining": 17, "expiringSoon": false}
                """);

        assertThat(studentDashboard()).contains("Tu plan").contains("Activa hasta el 31/10/2026");
    }

    @Test
    @DisplayName("an unverifiable subscription shows a calm status notice and the rest of the dashboard")
    void subscriptionUnavailable() throws Exception {
        stubMe(503, "{\"status\":503,\"code\":\"UNAVAILABLE\"}");

        String main = studentDashboard();

        assertThat(main).contains(CARD_HEADING)
                .containsPattern("role=\"status\"[^>]*>\\s*<p>No pudimos consultar tu suscripción ahora\\. "
                        + "Probá de nuevo en unos minutos\\.</p>")
                .doesNotContain("UNAVAILABLE").doesNotContain("role=\"alert\"");
        assertRestOfDashboard(main);
    }

    @Test
    @DisplayName("a staff member keeps the dashboard without a subscription card and no Billing call")
    void staff() throws Exception {
        Cookie session = logIn("INSTRUCTOR");

        String main = HtmlSections.mainOf(mockMvc.perform(get("/dashboard").cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(main).doesNotContain(CARD_HEADING).doesNotContain("suscripción");
        assertRestOfDashboard(main);
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlEqualTo(ME)));
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlEqualTo(PLANS)));
    }
}
