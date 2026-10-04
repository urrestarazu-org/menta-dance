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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Full-stack coverage for the in-person class pages (spec {@code
 * bff-physical-classes-view}): rendering against WireMock-stubbed catalog and
 * billing upstreams, the signed-in quote → checkout flow, and the
 * both-directions route-access regression.
 */
@DisplayName("Physical Classes View Integration Tests")
class PhysicalClassesViewIntegrationTest extends BaseIntegrationTest {

    private static final String COURSE_ID = "course-p";
    private static final String ACCESS_TOKEN = TestAccessTokens.withRole("STUDENT");

    private static final String LIST_BODY =
            """
            {"courses": [
              {"courseId": "course-v", "modality": "VIRTUAL", "title": "Ballet online", "level": "BEGINNER",
               "physical": null, "virtual": {"description": "x"}},
              {"courseId": "course-p", "modality": "PHYSICAL", "title": "Salsa inicial", "level": "BEGINNER",
               "physical": {"professorName": "María García", "dayOfWeek": "TUESDAY", "startTime": "19:00",
                            "capacity": 20},
               "virtual": null}
            ]}
            """;

    private static final String DETAIL_BODY =
            """
            {"courseId": "course-p", "modality": "PHYSICAL", "title": "Salsa inicial", "level": "BEGINNER",
             "physical": {"professorName": "María García", "dayOfWeek": "TUESDAY", "startTime": "19:00",
                          "capacity": 20,
                          "sessions": [
                            {"sessionId": "session-open", "scheduledAt": "2026-10-06T22:00:00Z", "capacity": 20,
                             "availableSpots": 17},
                            {"sessionId": "session-full", "scheduledAt": "2026-10-13T22:00:00Z", "capacity": 20,
                             "availableSpots": 0}
                          ]}}
            """;

    private static final String PRICING_BODY =
            """
            {"courseId": "course-p", "monthlyPrice": 12000.00, "currency": "ARS",
             "individualSurchargePercent": 20.00, "version": 1, "updatedAt": "2026-10-01T00:00:00Z"}
            """;

    private static final String QUOTE_BODY =
            """
            {"id": "quote-1", "courseId": "course-p", "purchaseType": "MONTHLY", "scheduledSessionCount": 4,
             "selectedSessionId": null, "amount": 12000.00, "currency": "ARS", "availability": "%s",
             "pricingVersion": 1, "createdAt": "2026-10-04T15:00:00Z", "expiresAt": "2026-10-04T16:00:00Z"}
            """;

    private static void stub(String method, String url, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.request(method, urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static void stubClassPage() {
        stub("GET", "/api/v1/catalog/courses/" + COURSE_ID, 200, DETAIL_BODY);
        stub("GET", "/api/v1/billing/physical/courses/" + COURSE_ID + "/pricing", 200, PRICING_BODY);
    }

    private Cookie login() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh_token_abc123")
                        .withBody(
                                """
                                {"access_token": "%s", "token_type": "Bearer", "expires_in": 3600}
                                """.formatted(ACCESS_TOKEN))));

        return mockMvc.perform(post("/login")
                        .param("username", "user@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");
    }

    // -- anonymous reads --

    @Test
    @DisplayName("anonymous class list shows only physical courses")
    void anonymousList_showsOnlyPhysicalCourses() throws Exception {
        stub("GET", "/api/v1/catalog/courses", 200, LIST_BODY);

        String body = mockMvc.perform(get("/classes"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Salsa inicial", "María García", "Martes 19:00", "/classes/course-p");
        assertThat(body).doesNotContain("Ballet online");
    }

    @Test
    @DisplayName("anonymous class page shows local times, free spots, sold out and a login prompt")
    void anonymousDetail_showsAvailabilityAndLoginPrompt() throws Exception {
        stubClassPage();

        String body = mockMvc.perform(get("/classes/{id}", COURSE_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Martes 6/10 · 19:00", "17 lugares libres", "Completo", "12.000,00 ARS");
        assertThat(body).contains("Iniciá sesión para comprar");
        assertThat(body).doesNotContain("Comprar el mes", "Comprar esta clase");
    }

    @Test
    @DisplayName("a class with no published price renders without buy actions")
    void unpricedDetail_hasNoBuyActions() throws Exception {
        stub("GET", "/api/v1/catalog/courses/" + COURSE_ID, 200, DETAIL_BODY);
        stub("GET", "/api/v1/billing/physical/courses/" + COURSE_ID + "/pricing", 404, "{\"title\":\"x\"}");
        Cookie session = login();

        String body = mockMvc.perform(get("/classes/{id}", COURSE_ID).cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("todavía no fue publicado", "17 lugares libres");
        assertThat(body).doesNotContain("Comprar el mes", "Comprar esta clase", "Iniciá sesión para comprar");
    }

    @Test
    @DisplayName("a virtual course id on the class page renders the error view as 404")
    void virtualId_isNotFound() throws Exception {
        stub("GET", "/api/v1/catalog/courses/" + COURSE_ID, 200,
                """
                {"courseId": "course-p", "title": "Ballet", "modules": [], "isPremium": true}
                """);

        mockMvc.perform(get("/classes/{id}", COURSE_ID)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("catalog downtime renders the shared error view without the upstream body")
    void catalogDown_rendersErrorView() throws Exception {
        stub("GET", "/api/v1/catalog/courses", 503, "{\"title\":\"CATALOG_DEGRADED\"}");

        String body = mockMvc.perform(get("/classes"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("CATALOG_DEGRADED");
    }

    // -- signed-in purchase flow --

    @Test
    @DisplayName("signed-in class page offers the month and only the open session")
    void signedInDetail_offersBuyActions() throws Exception {
        stubClassPage();
        Cookie session = login();

        String body = mockMvc.perform(get("/classes/{id}", COURSE_ID).cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Comprar el mes", "value=\"session-open\"");
        assertThat(body).doesNotContain("value=\"session-full\"", "Iniciá sesión para comprar");
    }

    @Test
    @DisplayName("quote then Mercado Pago checkout forwards the token and the page's idempotency key")
    void quoteThenMercadoPago_redirectsToProvider() throws Exception {
        stub("POST", "/api/v1/billing/physical/quotes", 201, QUOTE_BODY.formatted("AVAILABLE"));
        stub("POST", "/api/v1/billing/physical/purchases", 201,
                """
                {"paymentId": "pay-1", "quoteId": "quote-1", "status": "PENDING", "providerPreferenceId": "pref-1",
                 "checkoutUrl": "https://mp.example/checkout/pref-1", "externalReference": "SUB-pay-1",
                 "bankTransferInstructions": null}
                """);
        Cookie session = login();

        String quotePage = mockMvc.perform(post("/classes/{id}/quote", COURSE_ID)
                        .param("purchaseType", "MONTHLY")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(quotePage).contains("Mes completo", "12.000,00 ARS", "Pagar");
        Matcher key = Pattern.compile("name=\"idempotencyKey\" value=\"([^\"]+)\"").matcher(quotePage);
        assertThat(key.find()).isTrue();

        mockMvc.perform(post("/classes/purchases")
                        .param("courseId", COURSE_ID)
                        .param("quoteId", "quote-1")
                        .param("paymentMethod", "MERCADO_PAGO")
                        .param("idempotencyKey", key.group(1))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://mp.example/checkout/pref-1"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/api/v1/billing/physical/quotes"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/api/v1/billing/physical/purchases"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .withRequestBody(matchingJsonPath("$.idempotencyKey", equalTo(key.group(1)))));
    }

    @Test
    @DisplayName("a sold-out quote renders no payment form")
    void soldOutQuote_hasNoPaymentForm() throws Exception {
        stub("POST", "/api/v1/billing/physical/quotes", 201, QUOTE_BODY.formatted("UNAVAILABLE"));
        Cookie session = login();

        String body = mockMvc.perform(post("/classes/{id}/quote", COURSE_ID)
                        .param("purchaseType", "MONTHLY")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("No quedan lugares");
        assertThat(body).doesNotContain("idempotencyKey", "Pagar");
    }

    @Test
    @DisplayName("bank transfer checkout renders the instructions and reference")
    void bankTransfer_rendersInstructions() throws Exception {
        stub("POST", "/api/v1/billing/physical/purchases", 201,
                """
                {"paymentId": "pay-2", "quoteId": "quote-1", "status": "AWAITING_MANUAL_VERIFICATION",
                 "providerPreferenceId": null, "checkoutUrl": null, "externalReference": "SUB-pay-2",
                 "bankTransferInstructions": {"cbu": "0000003100000000000001", "alias": "menta.dance",
                   "holder": "Menta Dance SRL", "cuit": "30-12345678-9",
                   "amount": {"amount": 12000.00, "currency": "ARS"}, "reference": "SUB-pay-2"}}
                """);
        Cookie session = login();

        String body = mockMvc.perform(post("/classes/purchases")
                        .param("courseId", COURSE_ID)
                        .param("quoteId", "quote-1")
                        .param("paymentMethod", "BANK_TRANSFER")
                        .param("idempotencyKey", "key-1")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("0000003100000000000001", "menta.dance", "SUB-pay-2", "12.000,00 ARS");
    }

    @Test
    @DisplayName("a capacity conflict at checkout renders the sold-out message with a way back")
    void checkoutConflict_rendersSoldOut() throws Exception {
        stub("POST", "/api/v1/billing/physical/purchases", 409, "{\"code\":\"CAPACITY_UNAVAILABLE\"}");
        Cookie session = login();

        String body = mockMvc.perform(post("/classes/purchases")
                        .param("courseId", COURSE_ID)
                        .param("quoteId", "quote-1")
                        .param("paymentMethod", "MERCADO_PAGO")
                        .param("idempotencyKey", "key-1")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("No quedan lugares", "/classes/course-p");
        assertThat(body).doesNotContain("CAPACITY_UNAVAILABLE");
    }

    // -- route access, both directions --

    @Test
    @DisplayName("anonymous POSTs and deeper class paths still redirect to /login")
    void anonymousWritesAndSubpaths_stillRequireLogin() throws Exception {
        mockMvc.perform(post("/classes/{id}/quote", COURSE_ID).param("purchaseType", "MONTHLY").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post("/classes/purchases").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/classes/{id}/settings", COURSE_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
