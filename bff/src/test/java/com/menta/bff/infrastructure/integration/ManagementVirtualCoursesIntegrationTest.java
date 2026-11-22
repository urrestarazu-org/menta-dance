package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The virtual courses list of the management area (#367), read-only: {@code GET /gestion/cursos-virtuales} for the
 * administrator and the instructor. Each case logs in for real against the stubbed APIs. The list is what the
 * Virtual API returns for the caller: the BFF neither filters it nor repeats the rule that scopes it.
 */
@DisplayName("Management virtual courses list Integration Tests")
class ManagementVirtualCoursesIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/cursos-virtuales";
    private static final String API = "/api/v1/admin/virtual/courses";
    private static final String SECRET = "secret-detail";
    private static final String UNAVAILABLE = "No pudimos cargar los cursos virtuales en este momento. "
            + "Intentá nuevamente en unos minutos.";

    private static String course(String id, String title, String level, boolean premium, String status) {
        return ("{\"courseId\":\"%s\",\"title\":\"%s\",\"shortDescription\":\"Corta\",\"description\":\"Larga\","
                + "\"professorId\":\"0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00\",\"imageUrl\":\"https://img.example/t.png\","
                + "\"category\":\"Tango\",\"level\":\"%s\",\"premium\":%b,\"status\":\"%s\"}")
                .formatted(id, title, level, premium, status);
    }

    private static void stubList(String... courses) {
        stub(200, "{\"courses\":[" + String.join(",", courses) + "]}");
    }

    private static void stub(int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(API)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
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

    private MockHttpServletResponse page(String role) throws Exception {
        return mockMvc.perform(get(LIST).cookie(logIn(role))).andReturn().getResponse();
    }

    static Stream<Arguments> statusCases() {
        return Stream.of(
                Arguments.of("DRAFT", "Borrador"),
                Arguments.of("PUBLISHED", "Publicado"),
                Arguments.of("ARCHIVED", "Archivado"),
                Arguments.of("SUSPENDED", "Sin dato"));
    }

    @ParameterizedTest(name = "{0} is worded {1}")
    @MethodSource("statusCases")
    @DisplayName("the status is visible text, never only a colour, in an accessible table")
    void showsTheStatusAsText(String status, String label) throws Exception {
        stubList(course("c-1", "Tango base", "BEGINNER", false, status));

        MockHttpServletResponse response = page("ADMIN");
        String main = mainOf(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(main).containsPattern("<caption[^>]*>\\s*Cursos virtuales")
                .containsPattern("<th[^>]*scope=\"col\"[^>]*>\\s*Estado\\s*</th>")
                .containsPattern("<td[^>]*>\\s*<span[^>]*>\\s*" + label + "\\s*</span>\\s*</td>")
                .contains("role=\"region\"").contains("tabindex=\"0\"")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales\"[^>]*aria-current=\"page\"");
    }

    @Test
    @DisplayName("lists title, category, level and premium or free, and the instructor sees what the API gave them")
    void listsTheCourses() throws Exception {
        stubList(course("c-1", "Tango base", "BEGINNER", true, "PUBLISHED"),
                course("c-2", "Vals avanzado", "EXPERT", false, "DRAFT"));

        MockHttpServletResponse response = page("INSTRUCTOR");
        String main = mainOf(response.getContentAsString());

        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(main).contains("Tango base", "Vals avanzado", "Tango", "Principiante", "Premium", "Gratuito")
                .containsPattern("<td[^>]*>\\s*Sin dato\\s*</td>")
                .doesNotContain("imageUrl").doesNotContain("0a1b2c3d");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR"})
    @DisplayName("asks the API for the list with the Bearer of the user and no parameters, and shows what it answers")
    void asksTheApiAsTheUser(String role) throws Exception {
        stubList(course("c-1", "Curso de " + role, "ADVANCED", false, "DRAFT"));

        String main = mainOf(page(role).getContentAsString());

        assertThat(main).contains("Curso de " + role);
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlEqualTo(API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole(role))));
        WIRE_MOCK_SERVER.verify(1, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @Test
    @DisplayName("an empty list says so in Spanish, without a table")
    void emptyList() throws Exception {
        stubList();

        MockHttpServletResponse response = page("ADMIN");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(mainOf(response.getContentAsString())).contains("Todavía no hay cursos virtuales.")
                .doesNotContain("<table").doesNotContain(UNAVAILABLE);
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(500, "{\"detail\":\"" + SECRET + "\"}"),
                Arguments.of(403, "{\"code\":\"COURSE_NOT_OWNED\",\"detail\":\"" + SECRET + "\"}"),
                Arguments.of(409, "{\"code\":\"OTHER_CODE\",\"detail\":\"" + SECRET + "\"}"),
                Arguments.of(200, "{\"courses\":[{\"detail\":\"" + SECRET + "\"}]}"));
    }

    @ParameterizedTest(name = "API {0} -> 503")
    @MethodSource("failures")
    @DisplayName("when the API fails or refuses, the screen is a 503 with a notice and nothing of the answer")
    void unavailable(int status, String body) throws Exception {
        stub(status, body);

        MockHttpServletResponse response = page("ADMIN");
        String html = response.getContentAsString();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(mainOf(html)).contains(UNAVAILABLE).doesNotContain("<table").doesNotContain("Todavía no hay");
        assertThat(html).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("links only to the create form and to each course's edit form, with the title in the link name")
    void linksToTheForms() throws Exception {
        stubList(course("c-1", "Tango base", "BEGINNER", true, "DRAFT"));

        String html = page("ADMIN").getContentAsString();

        assertThat(mainOf(html)).containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales/nuevo\"[^>]*>Nuevo curso</a>")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales/c-1/editar\">Editar"
                        + "<span class=\"sr-only\">\\s*el curso Tango base</span></a>")
                .doesNotContain("<form").doesNotContain("Publicar").doesNotContain("Borrar");
        assertThat(html).doesNotContain("/api/v1");
    }
}
