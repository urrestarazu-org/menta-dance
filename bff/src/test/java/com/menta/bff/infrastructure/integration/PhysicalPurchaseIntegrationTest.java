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
    private static final String QUOTES = "/api/v1/billing/physical/quotes";

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
    @DisplayName("an anonymous visitor is invited to sign in instead of seeing quote buttons")
    void detailInvitesAnonymousToSignIn() throws Exception {
        String html = body(mockMvc.perform(get("/classes/" + COURSE_ID)).andExpect(status().isOk()));

        assertThat(html).contains("Ingresá para comprar").contains("href=\"/login\"")
                .doesNotContain("Cotizar").doesNotContain("<form");
    }

    @Test
    @DisplayName("a monthly quote shows course, price, sessions and expiry, and says online payment is not ready")
    void monthlyQuote() throws Exception {
        String html = monthlyQuotePage();

        assertThat(html).contains("Salsa en vivo", "Mensualidad", "12.500,50", "Sesiones del período: 4",
                "sábado 10 de octubre a las 10:00", "El pago online todavía no está disponible.");
        assertThat(html).doesNotContain("Pagar con Mercado Pago").doesNotContain("<form");
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

        assertThat(html).contains("Sin lugares disponibles").doesNotContain("El pago online todavía")
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
    @DisplayName("anonymous visitors are sent to the login when they post a quote")
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(post("/classes/" + COURSE_ID + "/cotizar").param("purchaseType", "MONTHLY").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(QUOTES)))).isEmpty();
    }

    @Test
    @DisplayName("a quote POST without the CSRF token is refused with 403")
    void postNeedsCsrf() throws Exception {
        mockMvc.perform(withSession(post("/classes/" + COURSE_ID + "/cotizar").param("purchaseType", "MONTHLY")))
                .andExpect(status().isForbidden());
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(QUOTES)))).isEmpty();
    }
}
