package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Cancellation of the student's own subscription (#363): the confirmation page, the POST that cancels and
 * the notice the dashboard shows afterwards, through the rendered HTML against a WireMock-stubbed Billing API.
 */
@DisplayName("Cancel subscription integration tests")
class CancelSubscriptionIntegrationTest extends BaseIntegrationTest {

    private static final String PATH = "/suscripciones/cancelar";
    private static final String ME = "/api/v1/billing/subscriptions/me";
    private static final String PLANS = "/api/v1/billing/plans";
    private static final String HISTORY = "/api/v1/billing/subscriptions/me/history";
    private static final String STUDENT_TOKEN = TestAccessTokens.withRole("STUDENT");

    private static final String ACTIVE_BODY =
            """
            {"subscriptionId": "s-1", "planId": "plan-1", "status": "ACTIVE",
            "endDate": "2026-12-31T15:00:00Z", "daysRemaining": 45, "expiringSoon": false}
            """;
    private static final String PLANS_BODY =
            """
            {"plans": [
              {"id": "plan-1", "name": "Plan Mensual", "description": "Un mes", "price": 4999.00,
               "currency": "ARS", "durationDays": 30, "featured": false}]}
            """;
    private static final String CANCELLED_BODY =
            """
            {"subscriptionId": "s-1", "status": "CANCELLED", "accessEndsAt": "2026-12-31T15:00:00Z",
             "cancellationPolicy": "SECRET-POLICY"}
            """;

    private static void stubJson(com.github.tomakehurst.wiremock.client.MappingBuilder mapping, int status,
            String body) {
        WIRE_MOCK_SERVER.stubFor(mapping.willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", status < 300 ? "application/json" : "application/problem+json")
                .withBody(body)));
    }

    private static void stubStudent(String meBody) {
        stubJson(WireMock.get(urlEqualTo(ME)), 200, meBody);
        stubJson(WireMock.get(urlEqualTo(PLANS)), 200, PLANS_BODY);
        stubJson(WireMock.get(urlEqualTo("/api/v1/virtual/courses/me/in-progress")), 200, "[]");
    }

    private static void stubCancel(int status, String body) {
        stubJson(WireMock.delete(urlEqualTo(ME)), status, body);
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

    private ResultActions cancel(Cookie session) throws Exception {
        return mockMvc.perform(post(PATH).cookie(session).with(csrf()));
    }

    private String dashboardOf(Cookie session) throws Exception {
        return HtmlSections.mainOf(mockMvc.perform(get("/dashboard").cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("a student sees what is cancelled, until when the access lasts, a CSRF-protected form and a way back")
    void confirmationPage() throws Exception {
        stubStudent(ACTIVE_BODY);

        String page = mockMvc.perform(get(PATH).cookie(logIn("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        String main = HtmlSections.mainOf(page);

        assertThat(main).contains("<h1", "Cancelar suscripción").contains("Plan Mensual")
                .contains("31/12/2026").contains("Mantenés el acceso")
                .containsPattern("<form[^>]*action=\"/suscripciones/cancelar\"[^>]*method=\"post\"")
                .contains("name=\"_csrf\"").contains("Sí, cancelar suscripción")
                .containsPattern("<a[^>]*href=\"/dashboard\"[^>]*>Volver</a>");
        assertThat(page).doesNotContain(STUDENT_TOKEN).doesNotContain("refresh-token-abc");
        WIRE_MOCK_SERVER.verify(0, deleteRequestedFor(urlEqualTo(ME)));
    }

    @Test
    @DisplayName("when the subscription cannot be read the page still asks, but invents no date")
    void confirmationWithoutDate() throws Exception {
        stubJson(WireMock.get(urlEqualTo(ME)), 503, "{\"status\":503}");

        String main = HtmlSections.mainOf(mockMvc.perform(get(PATH).cookie(logIn("STUDENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(main).contains("Sí, cancelar suscripción").doesNotContainPattern("\\d{2}/\\d{2}/\\d{4}");
    }

    @ParameterizedTest(name = "with a {0} status there is nothing to cancel: the student is sent to the dashboard")
    @ValueSource(strings = {"PENDING", "EXPIRED"})
    void nothingToConfirm(String status) throws Exception {
        stubStudent("{\"subscriptionId\": \"s-1\", \"planId\": \"plan-1\", \"status\": \"%s\", "
                .formatted(status) + "\"endDate\": \"2026-08-31T15:00:00Z\"}");

        mockMvc.perform(get(PATH).cookie(logIn("STUDENT")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
    }

    @ParameterizedTest(name = "a {0} gets a 403 on the confirmation page")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR", "RECEPTIONIST"})
    void staffConfirmationForbidden(String role) throws Exception {
        stubStudent(ACTIVE_BODY);

        mockMvc.perform(get(PATH).cookie(logIn(role))).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, WireMock.getRequestedFor(urlEqualTo(ME)));
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login from the confirmation page and from the POST")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(PATH).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        WIRE_MOCK_SERVER.verify(0, deleteRequestedFor(urlEqualTo(ME)));
    }

    @ParameterizedTest(name = "a {0} gets a 403 on the POST and no cancel call is made")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR", "RECEPTIONIST"})
    void staffPostForbidden(String role) throws Exception {
        stubCancel(200, CANCELLED_BODY);

        cancel(logIn(role)).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, deleteRequestedFor(urlEqualTo(ME)));
    }

    @Test
    @DisplayName("a POST without a CSRF token is refused and never reaches the upstream")
    void csrfLess() throws Exception {
        Cookie session = logIn("STUDENT");
        stubCancel(200, CANCELLED_BODY);

        mockMvc.perform(post(PATH).cookie(session)).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, deleteRequestedFor(urlEqualTo(ME)));
    }

    @Test
    @DisplayName("cancelling redirects to the dashboard, shows the notice once and never leaks the body or the token")
    void cancelled() throws Exception {
        Cookie session = logIn("STUDENT");
        stubCancel(200, CANCELLED_BODY);
        stubStudent(ACTIVE_BODY);

        cancel(session).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));

        WIRE_MOCK_SERVER.verify(1, deleteRequestedFor(urlEqualTo(ME))
                .withHeader("Authorization", equalTo("Bearer " + STUDENT_TOKEN)));
        String first = mockMvc.perform(get("/dashboard").cookie(session)).andReturn().getResponse()
                .getContentAsString();
        assertThat(HtmlSections.mainOf(first)).containsPattern(
                "role=\"status\"[^>]*>\\s*<p>Cancelamos tu suscripción: no se va a renovar\\. "
                        + "Mantenés el acceso hasta el 31/12/2026\\.</p>");
        assertThat(first).doesNotContain("SECRET-POLICY").doesNotContain(STUDENT_TOKEN);
        assertThat(dashboardOf(session)).doesNotContain("Cancelamos tu suscripción");
    }

    @ParameterizedTest(name = "a {0} answer is shown as: {2}")
    @CsvSource(delimiter = '|', value = {
        "404|SUBSCRIPTION_NOT_FOUND|No tenés una suscripción activa para cancelar.",
        "409|SUBSCRIPTION_CONFLICT|Tu suscripción cambió mientras la cancelábamos. Intentá de nuevo.",
        "503|SECRET_DETAIL|No pudimos confirmar la cancelación de tu suscripción."
    })
    void failures(int status, String code, String message) throws Exception {
        stubCancel(status, "{\"status\": " + status + ", \"code\": \"" + code + "\"}");
        stubStudent(ACTIVE_BODY);
        Cookie session = logIn("STUDENT");
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);

        try {
            cancel(session).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
            String page = mockMvc.perform(get("/dashboard").cookie(session)).andReturn().getResponse()
                    .getContentAsString();

            assertThat(HtmlSections.mainOf(page)).contains(message);
            assertThat(page).doesNotContain("SECRET_DETAIL").doesNotContain(STUDENT_TOKEN);
            assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains("SECRET_DETAIL")
                    || event.getFormattedMessage().contains(STUDENT_TOKEN));
        } finally {
            root.detachAppender(logs);
        }
        WIRE_MOCK_SERVER.verify(1, deleteRequestedFor(urlEqualTo(ME)));
    }

    @Test
    @DisplayName("after cancelling, the card says \"Cancelada, acceso hasta\" and offers no cancel button")
    void cardAfterCancelling() throws Exception {
        stubCancel(200, CANCELLED_BODY);
        stubJson(WireMock.get(urlEqualTo(ME)), 404, "{\"status\":404,\"code\":\"NO_SUBSCRIPTION\"}");
        stubJson(WireMock.get(urlEqualTo(PLANS)), 200, PLANS_BODY);
        stubJson(WireMock.get(urlEqualTo(HISTORY)), 200,
                "[{\"planId\":\"plan-1\",\"status\":\"CANCELLED\",\"startDate\":\"2026-11-01T15:00:00Z\","
                        + "\"endDate\":\"2099-12-31T15:00:00Z\",\"type\":\"PAID\"}]");
        stubJson(WireMock.get(urlEqualTo("/api/v1/virtual/courses/me/in-progress")), 200, "[]");

        Cookie session = logIn("STUDENT");
        cancel(session).andExpect(redirectedUrl("/dashboard"));
        String main = dashboardOf(session);

        assertThat(main).contains("Cancelada, acceso hasta el 31/12/2099").contains("Plan Mensual")
                .doesNotContain("Cancelar suscripción").doesNotContain("Todavía no tenés");
    }
}
