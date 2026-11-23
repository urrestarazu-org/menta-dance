package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The look of the structure screen of a virtual course (#849): the course header card, the module list with the
 * selected module chosen on the server by {@code ?modulo=} (first by default, an unknown id falls back to it), the
 * detail of that module with its lessons as cards, and the shell tabs. Behaviour, routes and accessible names are
 * covered by the module and lesson integration tests.
 */
@DisplayName("Management virtual structure layout Integration Tests")
class ManagementVirtualStructureLayoutIntegrationTest extends BaseIntegrationTest {

    private static final String ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String M1 = "11111111-1111-4111-8111-111111111111";
    private static final String M2 = "22222222-2222-4222-8222-222222222222";
    private static final String M3 = "33333333-3333-4333-8333-333333333333";
    private static final String STRUCTURE = "/gestion/cursos-virtuales/" + ID + "/estructura";
    private static final String COURSE_API = "/api/v1/admin/virtual/courses/" + ID;
    private static final String TITLE = "<script>boom()</script>";
    private static final String ESCAPED_TITLE = "&lt;script&gt;boom()&lt;/script&gt;";
    private static final Pattern SVG_OPEN = Pattern.compile("<svg\\b[^>]*>");

    private Cookie logIn() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(TestAccessTokens.withRole("ADMIN")))));
        Cookie session = mockMvc.perform(post("/login").param("email", "user@example.com")
                .param("password", "Password1").with(csrf())).andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private static String lesson(String id, String title, int order, int minutes, boolean free, String videoId) {
        return ("{\"lessonId\":\"%s\",\"title\":\"%s\",\"description\":\"Descripción\",\"durationMinutes\":%d,"
                + "\"isFree\":%s,\"order\":%d,\"videoId\":%s}").formatted(id, title, minutes, free, order,
                videoId == null ? "null" : "\"" + videoId + "\"");
    }

    private static String module(String id, String title, boolean preview, int order, String... lessons) {
        return "{\"moduleId\":\"%s\",\"title\":\"%s\",\"preview\":%s,\"order\":%d,\"lessons\":[%s]}"
                .formatted(id, title, preview, order, String.join(",", lessons));
    }

    private static void stubCourse(String status, String courseTitle, String... modules) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(COURSE_API)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(("{\"courseId\":\"%s\",\"title\":\"%s\","
                        + "\"shortDescription\":\"Corta\",\"description\":\"Larga\",\"professorId\":\"%s\","
                        + "\"thumbnailUrl\":\"https://img.example/t.png\",\"category\":\"Tango\","
                        + "\"level\":\"BEGINNER\",\"isPremium\":false,\"status\":\"%s\",\"modules\":[%s],"
                        + "\"stats\":{\"moduleCount\":%d,\"lessonCount\":3,\"totalDuration\":\"30m\"}}")
                        .formatted(ID, courseTitle, M1, status, String.join(",", modules), modules.length))));
    }

    /** Modules out of order on purpose; the first by order has two lessons, the second one, the third none. */
    private static void stubCourse() {
        stubCourse("PUBLISHED", "Tango base", module(M2, "Giros", false, 2,
                        lesson("l-3", "Giro único", 1, 5, true, "v-3")),
                module(M3, "Cierre", false, 3),
                module(M1, "Pasos básicos", true, 1, lesson("l-2", "Segundo paso", 2, 15, false, null),
                        lesson("l-1", "Primer paso", 1, 10, true, "v-1")));
    }

    private String render(String url) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get(url).cookie(logIn())).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContain("/api/v1");
        return mainOf(response.getContentAsString());
    }

    // ---- selected module ----

    @Test
    @DisplayName("the first module by order is selected by default: marked, with its lessons and not the others'")
    void firstModuleIsTheDefault() throws Exception {
        stubCourse();

        String main = render(STRUCTURE);

        assertThat(main).containsOnlyOnce("Seleccionado")
                .containsPattern("<a[^>]*href=\"" + STRUCTURE + "\\?modulo=" + M1 + "\"[^>]*aria-current=\"true\"")
                .containsSubsequence("Pasos básicos", "Seleccionado").contains("Primer paso", "Segundo paso")
                .doesNotContain("Giro único");
    }

    @Test
    @DisplayName("every module card links to its own selection, and only the selected one is marked current")
    void moduleCardsLinkToTheirSelection() throws Exception {
        stubCourse();

        String main = render(STRUCTURE + "?modulo=" + M2);

        assertThat(main).containsPattern("<a[^>]*href=\"" + STRUCTURE + "\\?modulo=" + M1 + "\"")
                .containsPattern("<a[^>]*href=\"" + STRUCTURE + "\\?modulo=" + M3 + "\"")
                .containsPattern("<a[^>]*href=\"" + STRUCTURE + "\\?modulo=" + M2 + "\"[^>]*aria-current=\"true\"");
        assertThat(count(main, "aria-current=\"true\"")).isEqualTo(1);
    }

    @Test
    @DisplayName("a valid modulo parameter selects that module and shows its lessons")
    void validModuleIsSelected() throws Exception {
        stubCourse();

        String main = render(STRUCTURE + "?modulo=" + M2);

        assertThat(main).contains("Giro único").doesNotContain("Primer paso").containsOnlyOnce("Seleccionado")
                .containsPattern("<a[^>]*href=\"" + STRUCTURE + "\\?modulo=" + M2 + "\"[^>]*aria-current=\"true\"");
    }

    @ParameterizedTest(name = "modulo={0}")
    @ValueSource(strings = {"no-existe", "44444444-4444-4444-8444-444444444444", ""})
    @DisplayName("an unknown or blank modulo falls back to the first module, without an error")
    void unknownModuleFallsBack(String modulo) throws Exception {
        stubCourse();

        String main = render(STRUCTURE + "?modulo=" + modulo);

        assertThat(main).contains("Primer paso").doesNotContain("Giro único").containsOnlyOnce("Seleccionado");
    }

    @Test
    @DisplayName("a selected module without lessons says so, and a course without modules shows only the notice")
    void emptyStates() throws Exception {
        stubCourse();
        assertThat(render(STRUCTURE + "?modulo=" + M3)).contains("Este módulo todavía no tiene lecciones.")
                .containsOnlyOnce("Seleccionado");

        stubCourse("DRAFT", "Tango base");
        assertThat(render(STRUCTURE)).contains("Este curso todavía no tiene módulos.", "Agregar módulo")
                .doesNotContain("Seleccionado").doesNotContain("Agregar lección");
    }

    // ---- cards ----

    @Test
    @DisplayName("the header card has the title, the status badge, the stats and the main action before the modules")
    void headerCard() throws Exception {
        stubCourse();

        String main = render(STRUCTURE);

        assertThat(main).containsSubsequence("Volver a los cursos virtuales", "Tango base", "Publicado", "3", "módulos",
                        "lecciones", "Duración total", "30m", "Agregar módulo", "Módulos",
                        "Organizá las lecciones y reordená los módulos")
                .containsPattern("<span[^>]*class=\"[^\"]*badge-success[^\"]*\"[^>]*>\\s*Publicado\\s*</span>")
                .containsPattern("<a[^>]*class=\"[^\"]*btn-primary[^\"]*\"[^>]*href=\"/gestion/cursos-virtuales/" + ID
                        + "/modulos/nuevo\"");
    }

    @Test
    @DisplayName("a draft course is a neutral badge and an archived one a warning, always with the status as text")
    void statusBadges() throws Exception {
        stubCourse("DRAFT", "Tango base", module(M1, "Pasos básicos", false, 1));
        assertThat(render(STRUCTURE)).containsPattern("badge-neutral[^>]*>\\s*Borrador\\s*</span>");

        stubCourse("ARCHIVED", "Tango base", module(M1, "Pasos básicos", false, 1));
        assertThat(render(STRUCTURE)).containsPattern("badge-warning[^>]*>\\s*Archivado\\s*</span>");
    }

    @Test
    @DisplayName("module cards show the number, the preview badge and the lesson count with the total minutes")
    void moduleCards() throws Exception {
        stubCourse();

        String main = render(STRUCTURE);

        assertThat(main).contains("2 lecciones · 25 min de duración", "1 lección · 5 min de duración",
                "0 lecciones · 0 min de duración").containsSubsequence("Pasos básicos", "Giros", "Cierre")
                .containsPattern("module-number[^>]*>\\s*1\\s*<").containsPattern("module-number[^>]*>\\s*3\\s*<");
        assertThat(count(main, "Vista previa")).as("the card and the detail of the selected module").isEqualTo(2);
    }

    @Test
    @DisplayName("lesson cards: access and video badges, a warning with icon and text when there is no video")
    void lessonCards() throws Exception {
        stubCourse();

        String main = render(STRUCTURE);

        assertThat(main).containsSubsequence("Primer paso", "Gratuita", "Con video", "10 min", "Segundo paso",
                        "Con suscripción", "Sin video", "15 min")
                .containsPattern("<span[^>]*class=\"[^\"]*badge-warning[^\"]*\"[^>]*>\\s*<svg[^>]*aria-hidden=\"true\""
                        + "[\\s\\S]*?</svg>\\s*Sin video\\s*</span>")
                .containsPattern("<span[^>]*class=\"[^\"]*badge-success[^\"]*\"[^>]*>\\s*Gratuita\\s*</span>")
                .containsPattern("<a[^>]*class=\"[^\"]*btn-ghost-danger[^\"]*\"[^>]*>Borrar<span class=\"sr-only\">");
    }

    @Test
    @DisplayName("the small buttons carry the btn-sm size and the detail has Agregar lección and Editar módulo")
    void smallButtonsAndDetailActions() throws Exception {
        stubCourse();

        String main = render(STRUCTURE);

        assertThat(main).containsPattern("<button[^>]*class=\"[^\"]*btn-sm[^\"]*\"[^>]*>[\\s\\S]*?Bajar<span")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales/" + ID + "/modulos/" + M1
                        + "/lecciones/nueva\"[^>]*>[\\s\\S]*?Agregar lección<span class=\"sr-only\">")
                .containsPattern("href=\"/gestion/cursos-virtuales/" + ID + "/modulos/nuevo\"[^>]*>[\\s\\S]*?"
                        + "Crear nuevo módulo");
    }

    // ---- safety and accessibility ----

    @Test
    @DisplayName("titles of the course, a module and a lesson are escaped")
    void titlesAreEscaped() throws Exception {
        stubCourse("DRAFT", TITLE, module(M1, TITLE, false, 1, lesson("l-1", TITLE, 1, 10, true, null)));

        MockHttpServletResponse response = mockMvc.perform(get(STRUCTURE).cookie(logIn())).andReturn().getResponse();

        assertThat(response.getContentAsString()).doesNotContain("<script>boom").contains(ESCAPED_TITLE);
    }

    @Test
    @DisplayName("every inline icon is decorative: aria-hidden and not focusable")
    void iconsAreDecorative() throws Exception {
        stubCourse();

        String main = render(STRUCTURE);

        assertThat(count(main, "<svg")).isPositive();
        SVG_OPEN.matcher(main).results().forEach(svg -> assertThat(svg.group())
                .contains("aria-hidden=\"true\"").contains("focusable=\"false\""));
    }

    @Test
    @DisplayName("the template and the alert fragment never use th:utext")
    void noUnescapedText() throws IOException {
        for (String path : new String[] {"templates/gestion/cursos-virtuales/estructura.html",
            "templates/fragments/components.html", "templates/fragments/icons.html", "templates/fragments/admin.html",
            "templates/fragments/management.html"}) {
            String source = new String(new ClassPathResource(path).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            assertThat(source).as(path).doesNotContain("th:utext");
        }
    }

    @Test
    @DisplayName("the shell tabs are pills: the current section keeps aria-current and takes the active tab style")
    void shellTabs() throws Exception {
        stubCourse();

        String main = render(STRUCTURE);

        assertThat(main).contains("<nav aria-label=\"Gestión\"");
        assertThat(tabOf(main, "Cursos virtuales")).contains("aria-current=\"page\"").contains("admin-tab-active");
        assertThat(tabOf(main, "Inicio")).contains("admin-tab").doesNotContain("aria-current")
                .doesNotContain("admin-tab-active");
    }

    private static String tabOf(String main, String label) {
        Matcher tab = Pattern.compile("<a([^>]*)>\\s*" + label + "\\s*</a>").matcher(main);
        assertThat(tab.find()).as("the tab " + label).isTrue();
        return tab.group(1);
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
