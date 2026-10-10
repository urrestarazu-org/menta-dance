package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Full-stack rendering coverage for the public physical classes pages (#383)
 * against a WireMock-stubbed catalog upstream.
 */
@DisplayName("Physical Classes Integration Tests")
class PhysicalClassesIntegrationTest extends BaseIntegrationTest {

    private static final String PHYSICAL_ID = "0b6f3a52-1111-4c3e-9a10-aaaaaaaaaaaa";
    private static final String DETAIL_URL = "/api/v1/catalog/courses/" + PHYSICAL_ID;

    private static final String VIRTUAL_ID = "7c1d9e00-2222-4c3e-9a10-bbbbbbbbbbbb";
    private static final String MISSING_ID = "99999999-3333-4c3e-9a10-cccccccccccc";
    private static final String LIST_URL = "/api/v1/catalog/courses";

    private static final String LIST_BODY =
            """
            {"courses": [
              {"courseId": "%s", "modality": "VIRTUAL", "title": "Ballet Básico", "level": "BEGINNER",
               "physical": null, "virtual": {"premium": false}},
              {"courseId": "%s", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
               "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                            "capacity": 12},
               "virtual": null}
            ]}
            """.formatted(VIRTUAL_ID, PHYSICAL_ID);

    private static final String PHYSICAL_DETAIL_BODY =
            """
            {"courseId": "%s", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
             "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                          "capacity": 12,
                          "sessions": [
                            {"sessionId": "s-1", "scheduledAt": "2026-10-12T21:00:00Z", "capacity": 12,
                             "availableSpots": 5},
                            {"sessionId": "s-2", "scheduledAt": "2026-10-13T01:30:00Z", "capacity": 12,
                             "availableSpots": 0}]}}
            """.formatted(PHYSICAL_ID);

    private static final String NO_SESSIONS_BODY =
            """
            {"courseId": "%s", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
             "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                          "capacity": 12, "sessions": []}}
            """.formatted(PHYSICAL_ID);

    private static final String VIRTUAL_DETAIL_BODY =
            """
            {"courseId": "%s", "title": "Ballet Básico", "description": "d", "level": "BEGINNER",
             "isPremium": false, "modules": [],
             "stats": {"moduleCount": 0, "lessonCount": 0, "totalDuration": "0m"}}
            """.formatted(VIRTUAL_ID);

    private static final Pattern HREF = Pattern.compile("href=\"(/[^\"]*)\"");

    private void stub(String url, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private String page(String uri, int expectedStatus) throws Exception {
        return mockMvc.perform(get(uri))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    /** Follows every internal link of the page anonymously; none may answer 4xx or 5xx. */
    private void assertNoBrokenLinks(String body) throws Exception {
        List<String> links = new ArrayList<>();
        Matcher matcher = HREF.matcher(body);
        while (matcher.find()) {
            links.add(matcher.group(1));
        }
        assertThat(links).isNotEmpty();
        for (String link : links) {
            int code = mockMvc.perform(get(link)).andReturn().getResponse().getStatus();
            assertThat(code).as("status of link %s", link).isLessThan(400);
        }
    }

    @Test
    @DisplayName("the virtual detail page answers 404, not 500, for a physical course id")
    void virtualDetailTreatsPhysicalPayloadAsNotFound() throws Exception {
        stub(DETAIL_URL, 200, PHYSICAL_DETAIL_BODY);

        String body = page("/courses/" + PHYSICAL_ID, 404);

        assertThat(body).doesNotContain("Salsa en vivo");
    }

    @Test
    @DisplayName("anonymous visitor sees the physical classes with level, professor, day and start time")
    void listRendersPhysicalClassesOnly() throws Exception {
        stub(LIST_URL, 200, LIST_BODY);
        stub(DETAIL_URL, 200, PHYSICAL_DETAIL_BODY);

        String body = page("/classes", 200);

        assertThat(body).contains("Salsa en vivo", "Avanzado", "María García", "Lunes 18:00");
        assertThat(body).contains("href=\"/classes/" + PHYSICAL_ID + "\"");
        assertThat(body).doesNotContain("Ballet Básico");
        assertNoBrokenLinks(body);
    }

    @Test
    @DisplayName("an empty physical catalog shows a Spanish message")
    void listRendersEmptyMessage() throws Exception {
        stub(LIST_URL, 200, "{\"courses\": []}");

        String body = page("/classes", 200);

        assertThat(body).contains("Todavía no hay clases presenciales disponibles.");
        assertThat(body).doesNotContain("<li");
    }

    @Test
    @DisplayName("a catalog outage renders a Spanish notice without upstream details")
    void listDegradesWithoutLeaking() throws Exception {
        stub(LIST_URL, 503, "{\"title\":\"Service unavailable\",\"detail\":\"db password\"}");

        String body = page("/classes", 503);

        assertThat(body).contains("No pudimos cargar las clases en este momento.");
        assertThat(body).doesNotContain("Service unavailable").doesNotContain("db password");
        assertNoBrokenLinks(body);
    }

    @Test
    @DisplayName("the detail shows professor, schedule and sessions in the academy zone with spots or Completo")
    void detailRendersSessionsInAcademyZone() throws Exception {
        stub(LIST_URL, 200, LIST_BODY);
        stub(DETAIL_URL, 200, PHYSICAL_DETAIL_BODY);

        String body = page("/classes/" + PHYSICAL_ID, 200);

        assertThat(body).contains("Salsa en vivo", "Avanzado", "María García", "Lunes 18:00");
        assertThat(body).contains("lunes 12 de octubre a las 18:00", "5 lugares libres");
        assertThat(body).contains("lunes 12 de octubre a las 22:30", "Completo");
        assertThat(body).doesNotContain("Cotizar");
        assertNoBrokenLinks(body);
    }

    @Test
    @DisplayName("a class without upcoming sessions still renders with a Spanish message")
    void detailWithoutSessions() throws Exception {
        stub(DETAIL_URL, 200, NO_SESSIONS_BODY);

        String body = page("/classes/" + PHYSICAL_ID, 200);

        assertThat(body).contains("Salsa en vivo", "Por ahora no hay próximas sesiones programadas.");
    }

    @Test
    @DisplayName("a virtual course id and an unknown id both answer 404")
    void detailAnswers404ForVirtualAndMissing() throws Exception {
        stub("/api/v1/catalog/courses/" + VIRTUAL_ID, 200, VIRTUAL_DETAIL_BODY);
        stub("/api/v1/catalog/courses/" + MISSING_ID, 404,
                "{\"type\":\"about:blank\",\"title\":\"Course not found\",\"status\":404}");

        String virtual = page("/classes/" + VIRTUAL_ID, 404);
        String missing = page("/classes/" + MISSING_ID, 404);

        assertThat(virtual).doesNotContain("Ballet Básico");
        assertThat(missing).doesNotContain("about:blank").doesNotContain("Course not found");
    }

    @Test
    @DisplayName("a catalog outage renders a degraded detail without a 500 or upstream details")
    void detailDegradesWithoutLeaking() throws Exception {
        stub(LIST_URL, 200, LIST_BODY);
        stub(DETAIL_URL, 503, "{\"title\":\"Service unavailable\",\"detail\":\"db password\"}");

        String body = page("/classes/" + PHYSICAL_ID, 503);

        assertThat(body).contains("No pudimos cargar esta clase en este momento.");
        assertThat(body).doesNotContain("Service unavailable").doesNotContain("db password");
        assertNoBrokenLinks(body);
    }

    @Test
    @DisplayName("the home links physical courses to their class page and offers the classes link")
    void homeLinksToClasses() throws Exception {
        stub(LIST_URL, 200, LIST_BODY);
        stub(DETAIL_URL, 200, PHYSICAL_DETAIL_BODY);
        stub("/api/v1/catalog/courses/" + VIRTUAL_ID, 200, VIRTUAL_DETAIL_BODY);

        String body = page("/", 200);

        assertThat(body).contains("href=\"/classes/" + PHYSICAL_ID + "\"");
        assertThat(body).contains("href=\"/classes\"").contains("Clases presenciales");
        assertNoBrokenLinks(body);
    }

    @Test
    @DisplayName("only GET /classes and GET /classes/{id} are public: other verbs and subpaths need a login")
    void onlyExactClassRoutesArePublic() throws Exception {
        mockMvc.perform(post("/classes").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post("/classes/{id}", PHYSICAL_ID).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/classes/{id}/sessions", PHYSICAL_ID))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/classes/{id}/sessions/{s}", PHYSICAL_ID, "s-1"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }
}
