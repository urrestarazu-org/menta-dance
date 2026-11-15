package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
 * The "Continuar" section of the dashboard (#363), through the rendered HTML against a
 * WireMock-stubbed Virtual API. It is independent of the subscription card: either one failing
 * leaves the other in place.
 */
@DisplayName("Dashboard continue-learning section integration tests")
class DashboardContinueLearningIntegrationTest extends BaseIntegrationTest {

    private static final String IN_PROGRESS = "/api/v1/virtual/courses/me/in-progress";
    private static final String ME = "/api/v1/billing/subscriptions/me";
    private static final String HEADING = "<h2 id=\"continue-heading\">Continuar</h2>";
    private static final String SUBSCRIPTION_HEADING = "Mi suscripción";

    private static void stubInProgress(int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(IN_PROGRESS)).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", status < 300 ? "application/json" : "application/problem+json")
                .withBody(body)));
    }

    private static void stubNoSubscription() {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(ME)).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":404,\"code\":\"NO_SUBSCRIPTION\"}")));
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

    @Test
    @DisplayName("a student sees each course in progress with its progress and a link to resume its lesson")
    void coursesInProgress() throws Exception {
        stubNoSubscription();
        stubInProgress(200,
                """
                [{"courseId": "c-2", "courseTitle": "Bachata", "completedLessons": 1, "totalLessons": 4,
                  "percentage": 25, "resumeLesson": {"lessonId": "l-21", "moduleId": "m-2",
                  "lessonTitle": "Paso básico", "positionSeconds": 42}},
                 {"courseId": "c-1", "courseTitle": "Salsa", "completedLessons": 3, "totalLessons": 10,
                  "percentage": 30, "resumeLesson": {"lessonId": "l-13", "moduleId": "m-1",
                  "lessonTitle": "Giros", "positionSeconds": 0}}]
                """);

        String main = studentDashboard();

        assertThat(main).contains(HEADING).contains(SUBSCRIPTION_HEADING)
                .contains("1 de 4 lecciones").contains("3 de 10 lecciones")
                .containsPattern("<progress max=\"100\" aria-labelledby=\"continue-course-0\" value=\"25\">25%"
                        + "</progress>\\s*<span>25% completado</span>")
                .contains("Te quedaste en: <span>Paso básico</span>")
                .contains("href=\"/courses/c-2/lessons/l-21\"").contains("href=\"/courses/c-1/lessons/l-13\"")
                .contains("Continuar<span class=\"sr-only\">: Bachata</span>")
                .doesNotContain("Todavía no empezaste");
        assertThat(main.indexOf("Bachata")).isLessThan(main.indexOf("Salsa"));
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(IN_PROGRESS))
                .withQueryParam("limit", equalTo("5"))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("STUDENT"))));
    }

    @Test
    @DisplayName("titles coming from the API are escaped, never rendered as markup")
    void escapesTitles() throws Exception {
        stubNoSubscription();
        stubInProgress(200,
                """
                [{"courseId": "c-1", "courseTitle": "<script>alert(1)</script>", "completedLessons": 1,
                  "totalLessons": 2, "percentage": 50, "resumeLesson": {"lessonId": "l-1", "moduleId": "m-1",
                  "lessonTitle": "<img src=x onerror=alert(2)>", "positionSeconds": 0}}]
                """);

        String main = studentDashboard();

        assertThat(main).doesNotContain("<script>alert(1)</script>").doesNotContain("<img src=x")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;").contains("&lt;img src=x onerror=alert(2)&gt;");
    }

    @Test
    @DisplayName("a student with no course in progress is pointed to the virtual course catalog")
    void noCourseInProgress() throws Exception {
        stubNoSubscription();
        stubInProgress(200, "[]");

        String main = studentDashboard();

        assertThat(main).contains(HEADING).contains("Todavía no empezaste ningún curso.")
                .contains("href=\"/?modality=virtual\"").doesNotContain("<progress");
    }

    @Test
    @DisplayName("a Virtual API failure shows a status notice in the section and keeps the subscription card")
    void virtualApiUnavailable() throws Exception {
        stubNoSubscription();
        stubInProgress(500, "{\"status\":500,\"code\":\"INTERNAL_ERROR\"}");

        String main = studentDashboard();

        assertThat(main).contains(HEADING)
                .containsPattern("role=\"status\"[^>]*>\\s*<p>No pudimos cargar tus cursos en progreso "
                        + "en este momento\\.</p>")
                .doesNotContain("INTERNAL_ERROR").doesNotContain("role=\"alert\"")
                .contains(SUBSCRIPTION_HEADING).contains("Todavía no tenés una suscripción.")
                .contains("Cerrar sesión");
    }

    @Test
    @DisplayName("a subscription failure does not hide the courses in progress")
    void subscriptionUnavailable() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(ME)).willReturn(aResponse().withStatus(503)));
        stubInProgress(200,
                """
                [{"courseId": "c-1", "courseTitle": "Salsa", "completedLessons": 3, "totalLessons": 10,
                  "percentage": 30, "resumeLesson": {"lessonId": "l-13", "moduleId": "m-1",
                  "lessonTitle": "Giros", "positionSeconds": 0}}]
                """);

        String main = studentDashboard();

        assertThat(main).contains("No pudimos consultar tu suscripción ahora.")
                .contains(HEADING).contains("3 de 10 lecciones").contains("href=\"/courses/c-1/lessons/l-13\"");
    }

    @Test
    @DisplayName("a staff member keeps the dashboard without the section and no Virtual API call")
    void staff() throws Exception {
        Cookie session = logIn("ADMIN");

        String main = HtmlSections.mainOf(mockMvc.perform(get("/dashboard").cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(main).doesNotContain("continue-heading").contains("Cerrar sesión");
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathEqualTo(IN_PROGRESS)));
    }
}
