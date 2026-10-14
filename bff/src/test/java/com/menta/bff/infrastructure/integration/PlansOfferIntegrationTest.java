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
 * The plans page offer by viewer and subscription state (#361, spec {@code bff-plans-view}),
 * through the rendered HTML against a WireMock-stubbed Billing API.
 */
@DisplayName("Plans offer integration tests")
class PlansOfferIntegrationTest extends BaseIntegrationTest {

    private static final String PLANS = "/api/v1/billing/plans";
    private static final String ME = "/api/v1/billing/subscriptions/me";
    private static final String SUBSCRIPTION_ID = "0f0e0d0c-1111-2222-3333-444444444444";

    private static final String PLANS_BODY =
            """
            {"plans": [
              {"id": "plan-1", "name": "Plan Mensual", "description": "Un mes", "price": 4999.00,
               "currency": "ARS", "durationDays": 30, "featured": false},
              {"id": "plan-2", "name": "Plan Anual", "description": "Un año", "price": 49990.00,
               "currency": "ARS", "durationDays": 365, "featured": true}]}
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

    private String plansPageAs(String role) throws Exception {
        return mockMvc.perform(get("/plans").cookie(logIn(role)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("an anonymous visitor sees a Suscribirme link to the login return and no subscription call")
    void anonymous() throws Exception {
        stubPlans();

        String body = mockMvc.perform(get("/plans")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Suscribirme").contains("href=\"/plans/suscribirme\"")
                .doesNotContain("action=\"/suscripciones\"");
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlEqualTo(ME)));
    }

    @Test
    @DisplayName("a student without a subscription sees a form per plan with CSRF and its own key")
    void studentWithoutSubscription() throws Exception {
        stubPlans();
        stubMe(404, "{\"status\":404,\"code\":\"NO_SUBSCRIPTION\"}");

        String body = plansPageAs("STUDENT");

        assertThat(body).contains("action=\"/suscripciones\"").contains("name=\"_csrf\"")
                .contains("name=\"planId\" value=\"plan-1\"").contains("name=\"planId\" value=\"plan-2\"")
                .contains("name=\"idempotencyKey\"")
                .doesNotContain("Ya tenés una suscripción").doesNotContain("pago pendiente");
        assertThat(body.split("name=\"idempotencyKey\"", -1)).hasSize(3);
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlEqualTo(ME))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("STUDENT"))));
    }

    @Test
    @DisplayName("an ACTIVE subscription shows its end date and no control")
    void active() throws Exception {
        stubPlans();
        stubMe(200, "{\"subscriptionId\":\"s-1\",\"status\":\"ACTIVE\",\"endDate\":\"2026-10-31T15:00:00Z\"}");

        String body = plansPageAs("STUDENT");

        assertThat(body).contains("Ya tenés una suscripción activa hasta el 31/10/2026.")
                .doesNotContain("Suscribirme").doesNotContain("/suscripciones");
    }

    @Test
    @DisplayName("a PENDING subscription shows the full id in a copyable element and no control")
    void pending() throws Exception {
        stubPlans();
        stubMe(200, "{\"subscriptionId\":\"" + SUBSCRIPTION_ID + "\",\"status\":\"PENDING\","
                + "\"checkoutUrl\":\"https://pay.example.com/c/1\"}");

        String body = plansPageAs("STUDENT");

        assertThat(body).contains("Tenés una suscripción con el pago pendiente")
                .containsPattern("<code[^>]*>" + SUBSCRIPTION_ID + "</code>")
                .doesNotContain("Suscribirme").doesNotContain("/suscripciones")
                .doesNotContain("pay.example.com");
    }

    @Test
    @DisplayName("an EXPIRED subscription shows its end date and offers Suscribirme again")
    void expired() throws Exception {
        stubPlans();
        stubMe(200, "{\"subscriptionId\":\"s-1\",\"status\":\"EXPIRED\",\"endDate\":\"2026-08-31T15:00:00Z\"}");

        String body = plansPageAs("STUDENT");

        assertThat(body).contains("Tu suscripción anterior venció el 31/08/2026.")
                .contains("Suscribirme").contains("action=\"/suscripciones\"");
    }

    @Test
    @DisplayName("another role sees the plans with no control and no subscription call")
    void otherRole() throws Exception {
        stubPlans();

        String body = plansPageAs("INSTRUCTOR");

        assertThat(body).contains("Plan Mensual").doesNotContain("Suscribirme").doesNotContain("/suscripciones");
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlEqualTo(ME)));
    }

    @Test
    @DisplayName("an unverifiable subscription fails closed: error view, no plans and no control")
    void failsClosed() throws Exception {
        stubPlans();
        stubMe(503, "{\"status\":503,\"code\":\"UNAVAILABLE\"}");
        Cookie session = logIn("STUDENT");

        String body = mockMvc.perform(get("/plans").cookie(session))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Suscribirme").doesNotContain("Plan Mensual")
                .doesNotContain("UNAVAILABLE");
    }

    @Test
    @DisplayName("an unexpected status also fails closed")
    void unexpectedStatusFailsClosed() throws Exception {
        stubPlans();
        stubMe(500, "{\"status\":500,\"code\":\"INTERNAL\"}");
        Cookie session = logIn("STUDENT");

        String body = mockMvc.perform(get("/plans").cookie(session))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Suscribirme");
    }
}
