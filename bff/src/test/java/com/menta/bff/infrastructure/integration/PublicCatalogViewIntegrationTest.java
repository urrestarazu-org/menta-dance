package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Full-stack rendering coverage for the public home page catalog (#358)
 * against a WireMock-stubbed catalog upstream.
 */
@DisplayName("Public Catalog View Integration Tests")
class PublicCatalogViewIntegrationTest extends BaseIntegrationTest {

    private static final String CATALOG_URL = "/api/v1/catalog/courses";

    private static final String LOGIN_BODY =
            """
            {"access_token": "%s", "token_type": "Bearer", "expires_in": 3600}
            """;

    private static final String MIXED_BODY =
            """
            {"courses": [
              {"courseId": "v-1", "modality": "VIRTUAL", "title": "Ballet Básico", "level": "BEGINNER",
               "physical": null, "virtual": {"premium": false}},
              {"courseId": "p-1", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
               "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                            "capacity": 12},
               "virtual": null}
            ]}
            """;

    private void stubCatalog(int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(CATALOG_URL))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private String anonymousHome(String uri) throws Exception {
        return mockMvc.perform(get(uri))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("anonymous visitor sees both modalities, labelled, with level and professor")
    void rendersBothModalities() throws Exception {
        stubCatalog(200, MIXED_BODY);

        String body = anonymousHome("/");

        assertThat(body).contains("Ballet Básico", "Virtual", "Principiante");
        assertThat(body).contains("Salsa en vivo", "Presencial", "Avanzado", "María García");
    }

    @Test
    @DisplayName("each course links to the detail page of its own modality")
    void linksEachModalityToItsDetailPage() throws Exception {
        stubCatalog(200, MIXED_BODY);

        String body = anonymousHome("/");

        assertThat(body).contains("href=\"/courses/v-1\"");
        assertThat(body).contains("href=\"/classes/p-1\"").doesNotContain("/courses/p-1");
    }

    @Test
    @DisplayName("the modality query parameter filters the catalog and an unknown value shows everything")
    void filtersByModality() throws Exception {
        stubCatalog(200, MIXED_BODY);

        String virtualOnly = anonymousHome("/?modality=virtual");
        assertThat(virtualOnly).contains("Ballet Básico").doesNotContain("Salsa en vivo");

        String physicalOnly = anonymousHome("/?modality=physical");
        assertThat(physicalOnly).contains("Salsa en vivo").doesNotContain("Ballet Básico");

        String unknown = anonymousHome("/?modality=hybrid");
        assertThat(unknown).contains("Ballet Básico").contains("Salsa en vivo");
    }

    @Test
    @DisplayName("an empty catalog shows a Spanish message instead of a list")
    void rendersEmptyMessage() throws Exception {
        stubCatalog(200, "{\"courses\": []}");

        String body = anonymousHome("/");

        assertThat(body).contains("Todavía no hay cursos disponibles.");
        assertThat(body).doesNotContain("<li");
    }

    @Test
    @DisplayName("an upstream failure renders the page with a Spanish notice and no upstream details")
    void rendersDegradedNoticeWithoutLeaking() throws Exception {
        stubCatalog(503, "{\"type\":\"about:blank\",\"title\":\"Service unavailable\",\"status\":503}");

        String body = anonymousHome("/");

        assertThat(body).contains("No pudimos cargar el catálogo de cursos en este momento.");
        assertThat(body).doesNotContain("about:blank").doesNotContain("Service unavailable");
        assertThat(body).doesNotContain("Todavía no hay cursos disponibles.");
        assertThat(body).contains("href=\"/login\"");
    }

    @Test
    @DisplayName("anonymous visitor sees the login and register links and no dashboard link")
    void anonymousSeesLoginLink() throws Exception {
        stubCatalog(200, MIXED_BODY);

        String body = anonymousHome("/");

        assertThat(body).contains("href=\"/login\"").contains("href=\"/registro\"");
        assertThat(body).doesNotContain("href=\"/dashboard\"");
    }

    @Test
    @DisplayName("authenticated user sees the dashboard link and no login or register link")
    void authenticatedSeesDashboardLink() throws Exception {
        stubCatalog(200, MIXED_BODY);

        Cookie sessionCookie = loginAndGetSessionCookie();

        String body = mockMvc.perform(get("/").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("href=\"/dashboard\"");
        assertThat(body).doesNotContain("href=\"/login\"").doesNotContain("href=\"/registro\"");
    }

    private Cookie loginAndGetSessionCookie() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh-token-abc")
                        .withBody(LOGIN_BODY.formatted(TestAccessTokens.withRole("STUDENT")))));

        return mockMvc.perform(post("/login")
                        .param("email", "student@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getCookie("SESSION");
    }
}
