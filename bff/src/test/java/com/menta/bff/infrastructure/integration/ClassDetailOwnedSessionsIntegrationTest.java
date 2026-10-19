package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The class page marks the sessions the signed-in student already holds (#542), through the
 * rendered HTML against a WireMock-stubbed upstream.
 */
@DisplayName("Class detail owned sessions integration tests")
class ClassDetailOwnedSessionsIntegrationTest extends BaseIntegrationTest {

    private static final String COURSE_ID = "0b6f3a52-1111-4c3e-9a10-aaaaaaaaaaaa";
    private static final String ACCESS_TOKEN = TestAccessTokens.withRole("STUDENT");
    private static final String MY_SESSIONS = "/api/v1/physical/sessions/me";
    private static final String OWNED_MARK = "Ya tenés lugar";
    private static final String QUOTE_BUTTON = "Cotizar solo esta clase";

    private static final String DETAIL_BODY =
            """
            {"courseId": "%s", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
             "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                          "capacity": 12,
                          "sessions": [
                            {"sessionId": "s-1", "scheduledAt": "2026-10-12T21:00:00Z", "capacity": 12,
                             "availableSpots": 5},
                            {"sessionId": "s-2", "scheduledAt": "2026-10-19T21:00:00Z", "capacity": 12,
                             "availableSpots": 3},
                            {"sessionId": "s-3", "scheduledAt": "2026-10-26T21:00:00Z", "capacity": 12,
                             "availableSpots": 0}]}}
            """.formatted(COURSE_ID);

    private static final String MY_SESSIONS_BODY =
            """
            {"from": "2026-10-13T03:00:00Z", "to": "2026-12-12T03:00:00Z", "sessions": [
              {"sessionId": "s-1", "courseId": "%s", "courseName": "Salsa en vivo", "instructorName": "María García",
               "scheduledAt": "2026-10-12T21:00:00Z", "checkInOpen": false},
              {"sessionId": "s-3", "courseId": "%s", "courseName": "Salsa en vivo", "instructorName": "María García",
               "scheduledAt": "2026-10-26T21:00:00Z", "checkInOpen": false}]}
            """.formatted(COURSE_ID, COURSE_ID);

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
        stubGet("/api/v1/catalog/courses/" + COURSE_ID, 200, DETAIL_BODY);
        stubGet("/api/v1/billing/physical/courses/" + COURSE_ID + "/pricing", 200,
                "{\"courseId\": \"" + COURSE_ID + "\", \"monthlyPrice\": 25000.00, \"currency\": \"ARS\"}");
    }

    private static void stubGet(String url, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(url)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private String studentPage() throws Exception {
        return mockMvc.perform(get("/classes/" + COURSE_ID).cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static int count(String html, String text) {
        return html.split(java.util.regex.Pattern.quote(text), -1).length - 1;
    }

    private static void assertOpenSessionsOfferQuotes(String html) {
        assertThat(count(html, OWNED_MARK)).isZero();
        assertThat(html).contains("name=\"selectedSessionId\" value=\"s-1\"")
                .contains("name=\"selectedSessionId\" value=\"s-2\"")
                .doesNotContain("name=\"selectedSessionId\" value=\"s-3\"");
        assertThat(count(html, QUOTE_BUTTON)).isEqualTo(2);
        assertThat(html).contains("Cotizar clases del mes");
    }

    @Test
    @DisplayName("a student sees 'Ya tenés lugar' and no quote button in the sessions they hold")
    void marksOwnedSessionsForSignedInStudent() throws Exception {
        stubGet(MY_SESSIONS, 200, MY_SESSIONS_BODY);

        String html = studentPage();

        assertThat(count(html, OWNED_MARK)).isEqualTo(2);
        assertThat(html).doesNotContain("name=\"selectedSessionId\" value=\"s-1\"");
        assertThat(html).contains("name=\"selectedSessionId\" value=\"s-2\"");
        assertThat(count(html, QUOTE_BUTTON)).isEqualTo(1);
        assertThat(html).contains("lunes 12 de octubre a las 18:00", "5 lugares libres", "Completo");
        assertThat(html).contains("Cotizar clases del mes");
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlEqualTo(MY_SESSIONS))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    @DisplayName("a student who holds none of the listed sessions keeps every quote button")
    void studentWithoutOwnedSessionsKeepsButtons() throws Exception {
        stubGet(MY_SESSIONS, 200, "{\"from\": \"2026-10-13T03:00:00Z\", \"to\": \"2026-12-12T03:00:00Z\", "
                + "\"sessions\": []}");

        assertOpenSessionsOfferQuotes(studentPage());
    }

    @Test
    @DisplayName("an anonymous visitor sees the page as before and no student call is made")
    void anonymousPageIsUnchanged() throws Exception {
        stubGet(MY_SESSIONS, 200, MY_SESSIONS_BODY);

        String html = mockMvc.perform(get("/classes/" + COURSE_ID))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain(OWNED_MARK).doesNotContain("Cotizar")
                .contains("Ingresá para comprar", "5 lugares libres", "3 lugares libres", "Completo");
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlEqualTo(MY_SESSIONS)));
    }

    @ParameterizedTest(name = "sessions/me answering {0} still renders the page, without marks")
    @ValueSource(ints = {401, 403, 404, 500, 503})
    @DisplayName("a failing student lookup degrades to the page without marks")
    void failingLookupDegrades(int upstreamStatus) throws Exception {
        stubGet(MY_SESSIONS, upstreamStatus, "{\"status\":" + upstreamStatus + ",\"detail\":\"db password\"}");

        String html = studentPage();

        assertOpenSessionsOfferQuotes(html);
        assertThat(html).doesNotContain("db password");
    }

    @Test
    @DisplayName("a dropped connection to the student lookup degrades to the page without marks")
    void droppedLookupDegrades() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(MY_SESSIONS))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertOpenSessionsOfferQuotes(studentPage());
    }
}
