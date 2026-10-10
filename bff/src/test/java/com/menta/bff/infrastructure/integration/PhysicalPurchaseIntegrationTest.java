package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Quote, pay and return pages of the physical class purchase (#365, cut A) through the
 * rendered HTML against a WireMock-stubbed upstream. Field names are read from the forms,
 * never hard-coded, so a template/controller mismatch breaks the build.
 */
@DisplayName("Physical purchase integration tests")
class PhysicalPurchaseIntegrationTest extends BaseIntegrationTest {

    private static final String COURSE_ID = "0b6f3a52-1111-4c3e-9a10-aaaaaaaaaaaa";
    private static final String QUOTE_ID = "00000000-0000-0000-0000-0000000000b1";
    private static final String ACCESS_TOKEN = TestAccessTokens.withRole("STUDENT");
    private static final String CATALOG = "/api/v1/catalog/courses/" + COURSE_ID;
    private static final String PAYMENT_ID = "00000000-0000-0000-0000-0000000000a1";
    private static final String CHECKOUT_URL = "https://pay.example.com/checkout/1";
    private static final String QUOTES = "/api/v1/billing/physical/quotes";
    private static final String PAYMENT = "/api/v1/billing/payments/" + PAYMENT_ID;
    private static final String PRICING = "/api/v1/billing/physical/courses/" + COURSE_ID + "/pricing";
    private static final String RETURN_PAGE = "/compras/retorno";
    private static final String PURCHASES = "/api/v1/billing/physical/purchases";

    private static final String DETAIL_BODY =
            """
            {"courseId": "%s", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
             "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                          "capacity": 12,
                          "sessions": [
                            {"sessionId": "s-1", "scheduledAt": "2026-10-12T21:00:00Z", "capacity": 12,
                             "availableSpots": 5},
                            {"sessionId": "s-2", "scheduledAt": "2026-10-13T01:30:00Z", "capacity": 12,
                             "availableSpots": 0}]}}
            """.formatted(COURSE_ID);

    private static final String LIST_BODY =
            """
            {"courses": [
              {"courseId": "%s", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
               "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                            "capacity": 12},
               "virtual": null}]}
            """.formatted(COURSE_ID);

    private static final String PRICING_BODY =
            """
            {"courseId": "%s", "monthlyPrice": 25000.00, "currency": "ARS", "individualSurchargePercent": 20,
             "version": 3, "updatedAt": "2026-10-10T12:00:00Z"}
            """.formatted(COURSE_ID);

    private static final Pattern FORM = Pattern.compile("<form[^>]*action=\"([^\"]+)\"[^>]*>(.*?)</form>",
            Pattern.DOTALL);
    private static final Pattern INPUT = Pattern.compile("<input[^>]*>");
    private static final Pattern NAME = Pattern.compile("name=\"([^\"]+)\"");
    private static final Pattern VALUE = Pattern.compile("value=\"([^\"]*)\"");
    private static final Pattern HREF = Pattern.compile("<a[^>]*href=\"(/[^\"]*)\"");

    private Cookie session;

    @BeforeEach
    void logIn() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(ACCESS_TOKEN))));
        session = mockMvc.perform(post("/login").param("email", "user@example.com")
                        .param("password", "Password1").with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        stubGet(CATALOG, 200, DETAIL_BODY);
        stubGet("/api/v1/catalog/courses", 200, LIST_BODY);
        stubGet(PRICING, 200, PRICING_BODY);
    }

    private static void stubGet(String url, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(url)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static void stubPost(String url, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(url)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/problem+json").withBody(body)));
    }

    private static void stubProblem(String url, int status, String code) {
        stubPost(url, status, "{\"status\":" + status + ",\"code\":\"" + code + "\"}");
    }

    private static void stubQuote(String purchaseType, String sessionId, String availability) {
        stubPost(QUOTES, 201,
                """
                {"id": "%s", "courseId": "%s", "purchaseType": "%s", "scheduledSessionCount": 4,
                 "selectedSessionId": %s, "amount": 12500.50, "currency": "ARS", "availability": "%s",
                 "pricingVersion": 3, "createdAt": "2026-10-10T12:00:00Z", "expiresAt": "2026-10-10T13:00:00Z"}
                """.formatted(QUOTE_ID, COURSE_ID, purchaseType, sessionId == null ? "null" : "\"" + sessionId + "\"",
                availability));
    }

    private static void stubCheckout(String checkoutUrl) {
        stubPost(PURCHASES, 201,
                """
                {"paymentId": "%s", "quoteId": "%s", "status": "PENDING", "providerPreferenceId": "pref-1",
                 "checkoutUrl": "%s", "externalReference": "ref-1", "bankTransferInstructions": null}
                """.formatted(PAYMENT_ID, QUOTE_ID, checkoutUrl));
    }

    private static void stubPayment(String status) {
        stubGet(PAYMENT, 200, "{\"status\":\"" + status
                + "\",\"createdAt\":\"2026-10-10T12:00:00Z\",\"updatedAt\":\"2026-10-10T12:01:00Z\"}");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder request) {
        return request.cookie(session);
    }

    /** Every form of the page: action to (field name to value), read from the HTML. */
    private static Map<String, Map<String, String>> forms(String html) {
        Map<String, Map<String, String>> forms = new LinkedHashMap<>();
        Matcher form = FORM.matcher(html);
        while (form.find()) {
            Map<String, String> fields = new LinkedHashMap<>();
            Matcher input = INPUT.matcher(form.group(2));
            while (input.find()) {
                Matcher name = NAME.matcher(input.group());
                Matcher value = VALUE.matcher(input.group());
                if (name.find() && !"_csrf".equals(name.group(1))) {
                    fields.put(name.group(1), value.find() ? value.group(1) : "");
                }
            }
            forms.put(form.group(1) + "#" + forms.size(), fields);
        }
        return forms;
    }

    private static Map<String, String> formPostingTo(String html, String action, String mustHaveField) {
        return forms(html).entrySet().stream()
                .filter(e -> e.getKey().startsWith(action + "#") && e.getValue().containsKey(mustHaveField))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
    }

    private static Map<String, String> monthlyForm(String detailHtml) {
        return forms(detailHtml).entrySet().stream()
                .filter(e -> e.getKey().startsWith("/classes/" + COURSE_ID + "/cotizar#"))
                .map(Map.Entry::getValue).filter(f -> "MONTHLY".equals(f.get("purchaseType")))
                .findFirst().orElseThrow();
    }

    private ResultActions submit(String action, Map<String, String> fields) throws Exception {
        MockHttpServletRequestBuilder request = withSession(post(action)).with(csrf());
        fields.forEach(request::param);
        return mockMvc.perform(request);
    }

    private String monthlyQuotePage() throws Exception {
        stubQuote("MONTHLY", null, "AVAILABLE");
        String detail = body(mockMvc.perform(withSession(get("/classes/" + COURSE_ID))).andExpect(status().isOk()));
        return body(submit("/classes/" + COURSE_ID + "/cotizar", monthlyForm(detail)).andExpect(status().isOk()));
    }

    /** Pays from the quote page, whose fields are read from its form. */
    private ResultActions payFromQuotePage(String quotePage) throws Exception {
        return submit("/compras", formPostingTo(quotePage, "/compras", "quoteId"));
    }

    /** The payment id the BFF kept in the server-side session, or {@code null}. */
    private String storedPaymentId() {
        for (String key : redisTemplate.keys("spring:session:sessions:*")) {
            Object value = redisTemplate.opsForHash().get(key, "sessionAttr:com.menta.bff.PENDING_PAYMENT_ID");
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private void assertLinksWork(String html) throws Exception {
        List<String> links = new ArrayList<>();
        Matcher href = HREF.matcher(html);
        while (href.find()) {
            links.add(href.group(1));
        }
        assertThat(links).isNotEmpty();
        for (String link : links) {
            int code = mockMvc.perform(withSession(get(link))).andReturn().getResponse().getStatus();
            assertThat(code).as("status of link %s", link).isLessThan(400);
        }
    }

    @Test
    @DisplayName("the class page offers quotes to a signed-in student, none for a full session")
    void detailOffersQuotesWhenSignedIn() throws Exception {
        String html = body(mockMvc.perform(withSession(get("/classes/" + COURSE_ID))).andExpect(status().isOk()));

        assertThat(html).contains("Cotizar clases del mes").doesNotContain("Ingresá para comprar");
        assertThat(html.split("Cotizar solo esta clase", -1)).hasSize(2);
        assertThat(formPostingTo(html, "/classes/" + COURSE_ID + "/cotizar", "selectedSessionId"))
                .containsEntry("purchaseType", "INDIVIDUAL").containsEntry("selectedSessionId", "s-1");
        assertThat(monthlyForm(html)).containsEntry("purchaseType", "MONTHLY");
        assertThat(html).contains("name=\"_csrf\"");
        assertLinksWork(html);
    }

    @Test
    @DisplayName("the class page shows the monthly price to a student and to an anonymous visitor, from a public call")
    void detailShowsTheMonthlyPrice() throws Exception {
        String student = body(mockMvc.perform(withSession(get("/classes/" + COURSE_ID))).andExpect(status().isOk()));
        String anonymous = body(mockMvc.perform(get("/classes/" + COURSE_ID)).andExpect(status().isOk()));

        assertThat(student).containsPattern("\\$\\p{Z}25\\.000,00 por mes").contains("Cotizar clases del mes");
        assertThat(anonymous).containsPattern("\\$\\p{Z}25\\.000,00 por mes").contains("Ingresá para comprar");
        assertThat(student + anonymous).doesNotContain("precio individual");
        assertThat(WIRE_MOCK_SERVER.findAll(getRequestedFor(urlEqualTo(PRICING)))).hasSize(2)
                .allSatisfy(request -> assertThat(request.header("Authorization").isPresent()).isFalse());
    }

    @Test
    @DisplayName("without a published price (404) the page still renders, with a notice and no purchase entry points")
    void detailWithoutPublishedPrice() throws Exception {
        stubProblemGet(PRICING, 404, "PHYSICAL_COURSE_PRICING_NOT_FOUND");

        for (String html : List.of(
                body(mockMvc.perform(withSession(get("/classes/" + COURSE_ID))).andExpect(status().isOk())),
                body(mockMvc.perform(get("/classes/" + COURSE_ID)).andExpect(status().isOk())))) {
            assertThat(html).contains("Salsa en vivo", "5 lugares libres")
                    .contains("Todavía no hay un precio publicado para esta clase.")
                    .doesNotContain("por mes").doesNotContain("Cotizar").doesNotContain("Ingresá para comprar")
                    .doesNotContain("<form").doesNotContain("PHYSICAL_COURSE_PRICING");
            assertLinksWork(html);
        }
    }

    @Test
    @DisplayName("when the price cannot be read (503) the page still renders, with a notice and no purchase buttons")
    void detailWithUnavailablePrice() throws Exception {
        stubProblemGet(PRICING, 503, "UNAVAILABLE");

        for (String html : List.of(
                body(mockMvc.perform(withSession(get("/classes/" + COURSE_ID))).andExpect(status().isOk())),
                body(mockMvc.perform(get("/classes/" + COURSE_ID)).andExpect(status().isOk())))) {
            assertThat(html).contains("Salsa en vivo", "5 lugares libres")
                    .contains("No pudimos consultar el precio en este momento.")
                    .doesNotContain("por mes").doesNotContain("Cotizar").doesNotContain("Ingresá para comprar")
                    .doesNotContain("<form").doesNotContain("UNAVAILABLE");
            assertLinksWork(html);
        }
    }

    @Test
    @DisplayName("an anonymous visitor is invited to sign in instead of seeing quote buttons")
    void detailInvitesAnonymousToSignIn() throws Exception {
        String html = body(mockMvc.perform(get("/classes/" + COURSE_ID)).andExpect(status().isOk()));

        assertThat(html).contains("Ingresá para comprar").contains("href=\"/login\"")
                .doesNotContain("Cotizar").doesNotContain("<form");
    }

    @Test
    @DisplayName("a monthly quote shows course, price, sessions and expiry, and a pay form read from the HTML")
    void monthlyQuote() throws Exception {
        String html = monthlyQuotePage();

        assertThat(html).contains("Salsa en vivo", "Mensualidad", "12.500,50", "Sesiones del período: 4",
                "sábado 10 de octubre a las 10:00");
        Map<String, String> pay = formPostingTo(html, "/compras", "quoteId");
        assertThat(pay).containsEntry("quoteId", QUOTE_ID).containsEntry("courseId", COURSE_ID);
        assertThat(pay.get("idempotencyKey")).matches("[0-9a-f-]{36}");
        assertThat(html).contains("Pagar con Mercado Pago").doesNotContain("El pago online todavía");
        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(QUOTES))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .withRequestBody(matchingJsonPath("$.purchaseType", equalTo("MONTHLY"))));
        assertLinksWork(html);
    }

    @Test
    @DisplayName("an individual quote sends the chosen session and names it")
    void individualQuote() throws Exception {
        stubQuote("INDIVIDUAL", "s-1", "AVAILABLE");
        String detail = body(mockMvc.perform(withSession(get("/classes/" + COURSE_ID))));

        String html = body(submit("/classes/" + COURSE_ID + "/cotizar",
                formPostingTo(detail, "/classes/" + COURSE_ID + "/cotizar", "selectedSessionId"))
                .andExpect(status().isOk()));

        assertThat(html).contains("Clase individual", "lunes 12 de octubre a las 18:00");
        assertLinksWork(html);
        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(QUOTES))
                .withRequestBody(matchingJsonPath("$.selectedSessionId", equalTo("s-1"))));
    }

    @Test
    @DisplayName("an UNAVAILABLE quote says there is no room and offers no payment")
    void unavailableQuote() throws Exception {
        stubQuote("MONTHLY", null, "UNAVAILABLE");
        String detail = body(mockMvc.perform(withSession(get("/classes/" + COURSE_ID))));

        String html = body(submit("/classes/" + COURSE_ID + "/cotizar", monthlyForm(detail))
                .andExpect(status().isOk()));

        assertThat(html).contains("Sin lugares disponibles").doesNotContain("Pagar con Mercado Pago")
                .doesNotContain("<form");
    }

    @Test
    @DisplayName("quote failures render a Spanish notice with the way back, with a fitting status")
    void quoteFailures() throws Exception {
        Map<String, String> monthly = Map.of("purchaseType", "MONTHLY");
        String action = "/classes/" + COURSE_ID + "/cotizar";

        stubProblem(QUOTES, 422, "NO_SCHEDULED_SESSIONS");
        String noSessions = body(submit(action, monthly).andExpect(status().isUnprocessableEntity()));
        assertThat(noSessions).contains("No hay clases programadas en este período")
                .contains("href=\"/classes/" + COURSE_ID + "\"");
        assertLinksWork(noSessions);

        stubProblem(QUOTES, 404, "PHYSICAL_COURSE_PRICING_NOT_FOUND");
        assertThat(body(submit(action, monthly).andExpect(status().isNotFound())))
                .contains("Todavía no hay un precio publicado");

        stubProblem(QUOTES, 400, "INVALID_REQUEST");
        assertThat(body(submit(action, monthly).andExpect(status().isBadRequest()))).contains("No pudimos procesar");

        stubProblem(QUOTES, 503, "UNAVAILABLE");
        assertThat(body(submit(action, monthly).andExpect(status().isServiceUnavailable())))
                .contains("No pudimos completar la operación").doesNotContain("UNAVAILABLE");

        submit(action, Map.of("purchaseType", "WEEKLY")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("paying redirects to the checkout and keeps the payment id in the server-side session")
    void payRedirectsAndKeepsThePaymentInTheSession() throws Exception {
        stubCheckout(CHECKOUT_URL);

        payFromQuotePage(monthlyQuotePage()).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(CHECKOUT_URL));

        assertThat(storedPaymentId()).contains(PAYMENT_ID);
        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(PURCHASES))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .withRequestBody(matchingJsonPath("$.quoteId", equalTo(QUOTE_ID)))
                .withRequestBody(matchingJsonPath("$.paymentMethod", equalTo("MERCADO_PAGO")))
                .withRequestBody(matchingJsonPath("$.idempotencyKey")));
    }

    @Test
    @DisplayName("a loopback http checkout is allowed for the local simulator; unusable URLs keep nothing")
    void checkoutUrlPolicy() throws Exception {
        String quotePage = monthlyQuotePage();
        for (String rejected : List.of("http://pay.example.com/checkout", "javascript:alert(1)", "https:///nohost")) {
            stubCheckout(rejected);
            String html = body(payFromQuotePage(quotePage).andExpect(status().isServiceUnavailable()));
            assertThat(html).contains("No pudimos completar la operación").doesNotContain(rejected);
            assertThat(storedPaymentId()).isNull();
        }
        stubCheckout("http://localhost:9999/checkout");
        payFromQuotePage(quotePage).andExpect(redirectedUrl("http://localhost:9999/checkout"));
        assertThat(storedPaymentId()).contains(PAYMENT_ID);
    }

    @Test
    @DisplayName("checkout failures: no room (409), expired quote (410), preference down (503), bad request (400)")
    void payFailures() throws Exception {
        String quotePage = monthlyQuotePage();
        String backLink = "href=\"/classes/" + COURSE_ID + "\"";

        stubProblem(PURCHASES, 409, "CAPACITY_UNAVAILABLE");
        String full = body(payFromQuotePage(quotePage).andExpect(status().isConflict()));
        assertThat(full).contains("Ya no hay lugares").contains(backLink);
        assertLinksWork(full);

        stubProblem(PURCHASES, 410, "PHYSICAL_COURSE_QUOTE_EXPIRED");
        assertThat(body(payFromQuotePage(quotePage).andExpect(status().isGone())))
                .contains("La cotización venció, pedí una nueva").contains(backLink);

        stubProblem(PURCHASES, 503, "PAYMENT_PREFERENCE_UNAVAILABLE");
        assertThat(body(payFromQuotePage(quotePage).andExpect(status().isServiceUnavailable())))
                .contains("No pudimos completar la operación").doesNotContain("PAYMENT_PREFERENCE");

        stubProblem(PURCHASES, 400, "INVALID_REQUEST");
        payFromQuotePage(quotePage).andExpect(status().isBadRequest());
        assertThat(storedPaymentId()).isNull();
    }

    @Test
    @DisplayName("a malformed quote id or an oversized idempotency key never reaches the API")
    void payRejectsMalformedForms() throws Exception {
        Map<String, String> fields = new LinkedHashMap<>(formPostingTo(monthlyQuotePage(), "/compras", "quoteId"));

        fields.put("quoteId", "not-a-uuid");
        submit("/compras", fields).andExpect(status().isBadRequest());
        fields.put("quoteId", QUOTE_ID);
        fields.put("idempotencyKey", "k".repeat(129));
        submit("/compras", fields).andExpect(status().isBadRequest());
        fields.put("idempotencyKey", " ");
        submit("/compras", fields).andExpect(status().isBadRequest());

        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(PURCHASES)))).isEmpty();
    }

    @Test
    @DisplayName("the return page without a payment in the session goes to the classes")
    void returnWithoutPayment() throws Exception {
        mockMvc.perform(withSession(get(RETURN_PAGE))).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/classes")).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("the return page words each final status, never says places are assigned, and forgets the payment")
    void returnFinalStatuses() throws Exception {
        for (Map.Entry<String, String> expected : Map.of(
                "COMPLETED", "¡Pago confirmado! Estamos asignando tus lugares",
                "REJECTED", "El pago fue rechazado",
                "CANCELLED", "El pago fue cancelado",
                "EXPIRED", "El pago venció").entrySet()) {
            stubCheckout(CHECKOUT_URL);
            payFromQuotePage(monthlyQuotePage()).andExpect(status().is3xxRedirection());
            stubPayment(expected.getKey());

            String html = body(mockMvc.perform(withSession(get(RETURN_PAGE))).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store")));

            assertThat(html).contains(expected.getValue()).contains("href=\"/classes\"")
                    .doesNotContain("ya están asignados");
            assertLinksWork(html);
            mockMvc.perform(withSession(get(RETURN_PAGE))).andExpect(redirectedUrl("/classes"));
        }
    }

    @Test
    @DisplayName("the return page ignores whatever the URL says and a pending payment is kept for the next refresh")
    void returnIgnoresUrlParametersAndKeepsPending() throws Exception {
        stubCheckout(CHECKOUT_URL);
        stubPayment("AWAITING_PROVIDER");
        payFromQuotePage(monthlyQuotePage());

        for (int i = 0; i < 2; i++) {
            String html = body(mockMvc.perform(withSession(get(RETURN_PAGE)
                            .param("status", "approved").param("payment_id", "99999").param("paymentId", "99999")))
                    .andExpect(status().isOk()));
            assertThat(html).contains("Estamos confirmando tu pago").doesNotContain("Pago confirmado");
        }
        assertThat(WIRE_MOCK_SERVER.findAll(getRequestedFor(urlEqualTo("/api/v1/billing/payments/99999")))).isEmpty();
    }

    @Test
    @DisplayName("a payment that is not the caller's (404) clears the session and goes to the classes")
    void returnForeignPayment() throws Exception {
        stubCheckout(CHECKOUT_URL);
        payFromQuotePage(monthlyQuotePage());
        stubProblemGet(PAYMENT, 404, "PAYMENT_NOT_FOUND");

        mockMvc.perform(withSession(get(RETURN_PAGE))).andExpect(redirectedUrl("/classes"));

        stubPayment("COMPLETED");
        mockMvc.perform(withSession(get(RETURN_PAGE))).andExpect(redirectedUrl("/classes"));
    }

    @Test
    @DisplayName("an outage while checking the payment shows a retry notice and keeps the payment")
    void returnWhileApiIsDown() throws Exception {
        stubCheckout(CHECKOUT_URL);
        payFromQuotePage(monthlyQuotePage());
        stubProblemGet(PAYMENT, 503, "UNAVAILABLE");

        String html = body(mockMvc.perform(withSession(get(RETURN_PAGE))).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store")));
        assertThat(html).contains("No pudimos completar la operación").contains("href=\"" + RETURN_PAGE + "\"");

        stubPayment("COMPLETED");
        assertThat(body(mockMvc.perform(withSession(get(RETURN_PAGE))).andExpect(status().isOk())))
                .contains("¡Pago confirmado!");
    }

    private static void stubProblemGet(String url, int status, String code) {
        stubGet(url, status, "{\"status\":" + status + ",\"code\":\"" + code + "\"}");
    }

    @Test
    @DisplayName("anonymous visitors are sent to the login when they post a quote")
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(post("/classes/" + COURSE_ID + "/cotizar").param("purchaseType", "MONTHLY").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post("/compras").with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get(RETURN_PAGE)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(QUOTES)))).isEmpty();
    }

    @Test
    @DisplayName("quote and pay POSTs without the CSRF token are refused with 403")
    void postNeedsCsrf() throws Exception {
        mockMvc.perform(withSession(post("/classes/" + COURSE_ID + "/cotizar").param("purchaseType", "MONTHLY")))
                .andExpect(status().isForbidden());
        mockMvc.perform(withSession(post("/compras").param("quoteId", QUOTE_ID).param("idempotencyKey", "k")))
                .andExpect(status().isForbidden());
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(QUOTES)))).isEmpty();
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(PURCHASES)))).isEmpty();
    }
}
