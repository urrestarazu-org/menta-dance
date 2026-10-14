package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The subscription checkout POST and the shared return page (#361, spec
 * {@code bff-plan-subscription-checkout}) through the rendered HTML against a WireMock-stubbed
 * Billing API.
 */
@DisplayName("Subscription checkout integration tests")
class SubscriptionCheckoutIntegrationTest extends BaseIntegrationTest {

    private static final String PLANS = "/api/v1/billing/plans";
    private static final String ME = "/api/v1/billing/subscriptions/me";
    private static final String SUBSCRIPTIONS = "/api/v1/billing/subscriptions";
    private static final String PAYMENT_ID = "00000000-0000-0000-0000-0000000000a1";
    private static final String PAYMENT = "/api/v1/billing/payments/" + PAYMENT_ID;
    private static final String CHECKOUT_URL = "https://pay.example.com/checkout/1";
    private static final String RETURN_PAGE = "/compras/retorno";

    private static final String PLANS_BODY =
            """
            {"plans": [{"id": "plan-1", "name": "Plan Mensual", "description": "Un mes", "price": 4999.00,
               "currency": "ARS", "durationDays": 30, "featured": false}]}
            """;

    private static final Pattern KEY = Pattern.compile("name=\"idempotencyKey\" value=\"([^\"]+)\"");

    private static void stubJson(com.github.tomakehurst.wiremock.client.MappingBuilder mapping, int status,
            String body) {
        WIRE_MOCK_SERVER.stubFor(mapping.willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", status < 300 ? "application/json" : "application/problem+json")
                .withBody(body)));
    }

    private static void stubCheckout(String checkoutUrl) {
        stubJson(WireMock.post(urlEqualTo(SUBSCRIPTIONS)), 201,
                """
                {"subscriptionId": "s-1", "paymentId": "%s", "planId": "plan-1", "status": "PENDING",
                 "providerPreferenceId": "pref-1", "externalReference": "ref-1", "checkoutUrl": "%s",
                 "overlapNotice": null, "bankTransferInstructions": null}
                """.formatted(PAYMENT_ID, checkoutUrl));
    }

    private static void stubPayment(String status) {
        stubJson(WireMock.get(urlEqualTo(PAYMENT)), 200, "{\"status\":\"" + status
                + "\",\"createdAt\":\"2026-10-10T12:00:00Z\",\"updatedAt\":\"2026-10-10T12:01:00Z\"}");
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

    private ResultActions subscribe(Cookie session, String planId, String key) throws Exception {
        return mockMvc.perform(post("/suscripciones").cookie(session).with(csrf())
                .param("planId", planId).param("idempotencyKey", key));
    }

    private String sessionAttribute(String name) {
        for (String key : redisTemplate.keys("spring:session:sessions:*")) {
            Object value = redisTemplate.opsForHash().get(key, "sessionAttr:com.menta.bff." + name);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    @Test
    @DisplayName("a student subscribes from the rendered form: redirect, upstream body and session writes")
    void studentSubscribes() throws Exception {
        stubJson(WireMock.get(urlEqualTo(PLANS)), 200, PLANS_BODY);
        stubJson(WireMock.get(urlEqualTo(ME)), 404, "{\"status\":404,\"code\":\"NO_SUBSCRIPTION\"}");
        stubCheckout(CHECKOUT_URL);
        Cookie session = logIn("STUDENT");
        String page = mockMvc.perform(get("/plans").cookie(session)).andReturn().getResponse().getContentAsString();
        Matcher key = KEY.matcher(page);
        assertThat(key.find()).isTrue();

        subscribe(session, "plan-1", key.group(1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(CHECKOUT_URL))
                .andExpect(header().string("Cache-Control", "no-store"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(SUBSCRIPTIONS))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("STUDENT")))
                .withRequestBody(matchingJsonPath("$.planId", equalTo("plan-1")))
                .withRequestBody(matchingJsonPath("$.paymentMethod", equalTo("MERCADO_PAGO")))
                .withRequestBody(matchingJsonPath("$.idempotencyKey", equalTo(key.group(1)))));
        assertThat(sessionAttribute("PENDING_PAYMENT_ID")).contains(PAYMENT_ID);
        assertThat(sessionAttribute("PENDING_PURCHASE_KIND")).contains("SUBSCRIPTION");
    }

    @Test
    @DisplayName("a checkout URL outside the policy is never followed and nothing is stored")
    void rejectedUrl() throws Exception {
        stubCheckout("http://evil.example.com/c/1");
        Cookie session = logIn("STUDENT");

        String body = subscribe(session, "plan-1", "key-1")
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("evil.example.com");
        assertThat(sessionAttribute("PENDING_PAYMENT_ID")).isNull();
    }

    @Test
    @DisplayName("a POST without CSRF is refused and never reaches the upstream")
    void csrfLess() throws Exception {
        Cookie session = logIn("STUDENT");

        mockMvc.perform(post("/suscripciones").cookie(session)
                        .param("planId", "plan-1").param("idempotencyKey", "key-1"))
                .andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlEqualTo(SUBSCRIPTIONS)));
    }

    @Test
    @DisplayName("a non-student role is refused and never reaches the upstream")
    void instructorRefused() throws Exception {
        Cookie session = logIn("INSTRUCTOR");

        subscribe(session, "plan-1", "key-1").andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlEqualTo(SUBSCRIPTIONS)));
    }

    @Test
    @DisplayName("an anonymous POST goes to the login page")
    void anonymousRedirected() throws Exception {
        mockMvc.perform(post("/suscripciones").with(csrf())
                        .param("planId", "plan-1").param("idempotencyKey", "key-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlEqualTo(SUBSCRIPTIONS)));
    }

    @Test
    @DisplayName("409 already active shows the end date without upstream details")
    void alreadyActive() throws Exception {
        stubJson(WireMock.post(urlEqualTo(SUBSCRIPTIONS)), 409,
                "{\"status\":409,\"code\":\"SUBSCRIPTION_ALREADY_ACTIVE\","
                        + "\"currentEndDate\":\"2026-10-31T15:00:00Z\"}");
        Cookie session = logIn("STUDENT");

        String body = subscribe(session, "plan-1", "key-1").andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Ya tenés una suscripción vigente hasta el 31/10/2026.")
                .doesNotContain("SUBSCRIPTION_ALREADY_ACTIVE");
    }
}
