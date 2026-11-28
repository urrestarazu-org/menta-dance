package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.API;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.LESSON_EDIT;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.SECRET;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.VIDEO_ID;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.lesson;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.missing;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.page;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.problem;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.video;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

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
 * The videos list of the management area (#842), read-only: {@code GET /gestion/videos} for the administrator and
 * the instructor. Each case logs in for real against the stubbed APIs. The list is what the Virtual API returns for
 * the caller: the BFF neither filters it nor repeats the rule that scopes it, and it shows everything the API says,
 * including the videos that a lesson uses and the library no longer has.
 */
@DisplayName("Management videos list Integration Tests")
class ManagementVideosIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/videos";
    private static final String MISSING_NOTICE = "Este video ya no existe en la biblioteca.";
    private static final String UNAVAILABLE = "No pudimos cargar los videos en este momento. "
            + "Intentá nuevamente en unos minutos.";
    private static final String LIBRARY_DOWN = "La biblioteca de videos no está disponible en este momento. "
            + "Intentá nuevamente en unos minutos.";

    private Cookie logIn(String role) throws Exception {
        return VideoApiFixtures.logIn(mockMvc, WIRE_MOCK_SERVER, role);
    }

    private static void stubList(String body) {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "GET", API, 200, body);
    }

    private MockHttpServletResponse open(String role) throws Exception {
        return mockMvc.perform(get(LIST).cookie(logIn(role))).andReturn().getResponse();
    }

    /** The {@code <main>} of the response, after checking what no screen of these may leak or cache. */
    private static String render(MockHttpServletResponse response) throws Exception {
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsString()).doesNotContain("/api/v1").doesNotContain(SECRET);
        return mainOf(response.getContentAsString());
    }

    // ---- what each row says ----

    static Stream<Arguments> states() {
        return Stream.of(
                Arguments.of("FINISHED", true, "Con MP4", "badge-success"),
                Arguments.of("FINISHED", false, "Sin MP4", "badge-warning"),
                Arguments.of("PROCESSING", false, "Procesando", "badge-warning"),
                Arguments.of("TRANSCODING", true, "Procesando", "badge-warning"),
                Arguments.of("CREATED", false, "Esperando el archivo", "badge-neutral"),
                Arguments.of("ERROR", false, "Con error", "badge-danger"),
                Arguments.of("UPLOAD_FAILED", false, "Con error", "badge-danger"),
                Arguments.of("SOMETHING_NEW", false, "Sin dato", "badge-neutral"));
    }

    @ParameterizedTest(name = "{0} (MP4 {1}) is worded {2}")
    @MethodSource("states")
    @DisplayName("the state is visible text with a badge, never only a colour, in an accessible table")
    void showsTheStateAsText(String status, boolean mp4, String label, String badge) throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "Abrazo", status, 65, null, mp4, lesson())));

        MockHttpServletResponse response = open("ADMIN");
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(main).containsPattern("<caption[^>]*>\\s*Videos \\(<span>1</span> en total\\)")
                .containsPattern("<th[^>]*scope=\"col\"[^>]*>\\s*Estado\\s*</th>")
                .containsPattern("<span[^>]*class=\"[^\"]*" + badge + "[^\"]*\"[^>]*>" + label + "</span>")
                .contains("role=\"region\"").contains("tabindex=\"0\"")
                .containsPattern("<a[^>]*href=\"/gestion/videos\"[^>]*aria-current=\"page\"");
    }

    @Test
    @DisplayName("a video without MP4 says in words that it will not play with the current CDN configuration")
    void withoutMp4SaysItWillNotPlay() throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "Abrazo", "FINISHED", 65, null, false, lesson())));

        String main = render(open("ADMIN"));

        assertThat(main).contains("Sin MP4").contains("No se va a reproducir con la configuración actual del CDN.");
    }

    @Test
    @DisplayName("shows the title, the length and the lessons that use it, each linked to its edit screen")
    void showsTitleDurationAndLessons() throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "Abrazo", "FINISHED", 3725, null, true, lesson(),
                lesson("Vals", "Giros", "El giro"))));

        String main = render(open("ADMIN"));

        assertThat(main).containsPattern("<th[^>]*scope=\"row\"[^>]*>\\s*Abrazo\\s*</th>")
                .contains("1:02:05")
                .containsPattern("<a[^>]*href=\"" + LESSON_EDIT + "\"[^>]*>\\s*Tango desde cero\\s*›\\s*Primeros pasos"
                        + "\\s*›\\s*El abrazo\\s*</a>")
                .contains("Vals", "Giros", "El giro");
    }

    @Test
    @DisplayName("a video no lesson uses says so")
    void unusedVideo() throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "Suelto", "FINISHED", 30, null, true)));

        assertThat(render(open("ADMIN"))).contains("Ninguna lección lo usa.");
    }

    @Test
    @DisplayName("escapes every text that comes from the API")
    void escapesEverything() throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "<script>boom()</script>", "FINISHED", 30, null, true,
                lesson("<b>c</b>", "<i>m</i>", "<u>l</u>"))));

        String main = render(open("ADMIN"));

        assertThat(main).contains("&lt;script&gt;boom()&lt;/script&gt;", "&lt;b&gt;c&lt;/b&gt;")
                .doesNotContain("<script>boom").doesNotContain("<b>c</b>").doesNotContain("<u>l</u>");
    }

    // ---- the preview place: thumbnail, notice or placeholder ----

    @Test
    @DisplayName("a video that no longer exists shows a text notice where the thumbnail goes, its lessons and its id")
    void missingInLibrary() throws Exception {
        stubList(page(0, 1, 1, missing("gone-123", lesson())));

        String main = render(open("INSTRUCTOR"));

        assertThat(main).contains(MISSING_NOTICE).contains("Ya no existe").contains("Sin título")
                .contains("Una lección usa este video, pero la biblioteca ya no lo tiene.")
                .containsPattern("Identificador:\\s*<code[^>]*>gone-123</code>")
                .containsPattern("<a[^>]*href=\"" + LESSON_EDIT + "\"")
                .doesNotContain("<img")
                .doesNotContain("/gestion/videos/gone-123/renombrar").doesNotContain("/gestion/videos/gone-123/borrar");
    }

    @Test
    @DisplayName("a missing video is not hidden even when the API also sends a thumbnail for it")
    void missingIgnoresThumbnail() throws Exception {
        stubList(page(0, 1, 1, missing("gone-123", lesson()).replace("\"thumbnailUrl\":null",
                "\"thumbnailUrl\":\"https://cdn.example/t.jpg\"")));

        String main = render(open("ADMIN"));

        assertThat(main).contains(MISSING_NOTICE).doesNotContain("<img");
    }

    @Test
    @DisplayName("an https thumbnail is an image with an empty alt (the title is next to it)")
    void httpsThumbnail() throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "Abrazo", "FINISHED", 30, "https://vz-1.b-cdn.net/x/thumb.jpg", true,
                lesson())));

        String main = render(open("ADMIN"));

        assertThat(main).containsPattern("<img[^>]*src=\"https://vz-1.b-cdn.net/x/thumb.jpg\"[^>]*alt=\"\"")
                .doesNotContain("Sin miniatura").doesNotContain(MISSING_NOTICE);
    }

    static Stream<String> unsafeThumbnails() {
        return Stream.of(null, "", "http://cdn.example/t.jpg", "javascript:alert(1)", "data:image/png;base64,AA",
                "/relative/t.jpg", "https://cdn.example/t.jpg\" onerror=\"alert(1)");
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("unsafeThumbnails")
    @DisplayName("no thumbnail, or one that is not https, is a neutral placeholder with text")
    void placeholder(String thumbnail) throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "Abrazo", "FINISHED", 30, thumbnail, true, lesson())));

        String main = render(open("ADMIN"));

        assertThat(main).contains("Sin miniatura").doesNotContain("<img").doesNotContain("onerror")
                .doesNotContain(MISSING_NOTICE);
    }

    // ---- actions by role ----

    @Test
    @DisplayName("the administrator can rename and delete; the links carry the title and are named after it")
    void adminActions() throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "Abrazo & vals", "FINISHED", 30, null, true)));

        String main = render(open("ADMIN"));

        assertThat(main)
                .containsPattern("<a[^>]*href=\"/gestion/videos/" + VIDEO_ID
                        + "/renombrar\\?titulo=Abrazo%20%26%20vals\""
                        + "[^>]*>Renombrar<span class=\"sr-only\">\\s*el video Abrazo &amp; vals</span></a>")
                .containsPattern("<a[^>]*href=\"/gestion/videos/" + VIDEO_ID + "/borrar\\?titulo=Abrazo%20%26%20vals\""
                        + "[^>]*>Borrar<span class=\"sr-only\">\\s*el video Abrazo &amp; vals</span></a>")
;
    }

    @Test
    @DisplayName("the instructor can rename but does not see the delete action")
    void instructorActions() throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "Abrazo", "FINISHED", 30, null, true, lesson())));

        String main = render(open("INSTRUCTOR"));

        assertThat(main).contains("/gestion/videos/" + VIDEO_ID + "/renombrar")
                .doesNotContain("/borrar").doesNotContain("Borrar");
    }

    @Test
    @DisplayName("a video without a title offers renaming with an empty field, not with the stand-in text")
    void untitledVideoRenamesWithoutPrefill() throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, null, "FINISHED", 30, null, true)));

        String main = render(open("ADMIN"));

        assertThat(main).contains("Sin título")
                .containsPattern("href=\"/gestion/videos/" + VIDEO_ID + "/renombrar\"");
    }

    // ---- search and paging ----

    @Test
    @DisplayName("asks the API as the user, with the first page, a fixed size and no search")
    void asksTheApiAsTheUser() throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "Abrazo", "FINISHED", 30, null, true)));

        render(open("ADMIN"));

        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlPathEqualTo(API)).withQueryParam("page", equalTo("0"))
                .withQueryParam("size", equalTo("20")).withoutQueryParam("search")
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN"))));
        WIRE_MOCK_SERVER.verify(1, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @Test
    @DisplayName("the search goes to the API, comes back in the field escaped and travels in the pagination links")
    void search() throws Exception {
        stubList(page(1, 3, 45, video(VIDEO_ID, "Abrazo", "FINISHED", 30, null, true)));

        MockHttpServletResponse response = mockMvc.perform(get(LIST).param("q", "  tango \"x\" <b>  ")
                .param("page", "1").cookie(logIn("ADMIN"))).andReturn().getResponse();
        String main = render(response);

        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlPathEqualTo(API))
                .withQueryParam("search", equalTo("tango \"x\" <b>")).withQueryParam("page", equalTo("1")));
        assertThat(main).containsPattern("<form[^>]*action=\"/gestion/videos\"[^>]*method=\"get\"[^>]*role=\"search\"")
                .containsPattern("<label[^>]*for=\"q\"[^>]*>\\s*Buscar por título\\s*</label>")
                .containsPattern("<input[^>]*id=\"q\"[^>]*name=\"q\"[^>]*value=\"tango &quot;x&quot; &lt;b&gt;\"")
                .contains("rel=\"prev\"", "rel=\"next\"")
                .containsPattern("href=\"/gestion/videos\\?q=tango%20%22x%22%20%3Cb%3E&amp;page=0\"")
                .containsPattern("href=\"/gestion/videos\\?q=tango%20%22x%22%20%3Cb%3E&amp;page=2\"")
                .contains("Página 2 de 3").doesNotContain("<b>");
    }

    @Test
    @DisplayName("a single page shows no pagination; the first page has no previous link")
    void paginationEdges() throws Exception {
        stubList(page(0, 1, 1, video(VIDEO_ID, "Abrazo", "FINISHED", 30, null, true)));
        assertThat(render(open("ADMIN"))).doesNotContain("Paginación");

        stubList(page(0, 2, 25, video(VIDEO_ID, "Abrazo", "FINISHED", 30, null, true)));
        String first = render(open("ADMIN"));

        assertThat(first).containsPattern("<a[^>]*aria-disabled=\"true\"[^>]*>Anterior</a>")
                .containsPattern("<a[^>]*rel=\"next\"[^>]*>Siguiente</a>");
    }

    @Test
    @DisplayName("a page that is not a number is a 400 and the API is not asked")
    void pageNotANumber() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get(LIST).param("page", "abc").cookie(logIn("ADMIN")))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @Test
    @DisplayName("an empty library says so, and an empty search says nothing matches, without a table")
    void emptyStates() throws Exception {
        stubList(page(0, 0, 0));
        MockHttpServletResponse plain = open("ADMIN");
        MockHttpServletResponse searched = mockMvc.perform(get(LIST).param("q", "zzz").cookie(logIn("ADMIN")))
                .andReturn().getResponse();

        assertThat(plain.getStatus()).isEqualTo(200);
        assertThat(render(plain)).contains("Todavía no hay videos.").doesNotContain("<table")
                .doesNotContain(UNAVAILABLE);
        assertThat(render(searched)).contains("No hay videos que coincidan con la búsqueda.")
                .doesNotContain("<table").doesNotContain("Todavía no hay videos.");
    }

    // ---- failures ----

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(500, "{\"detail\":\"" + SECRET + "\"}", UNAVAILABLE),
                Arguments.of(401, problem(401, "INVALID_TOKEN"), UNAVAILABLE),
                Arguments.of(400, problem(400, "INVALID_REQUEST"), UNAVAILABLE),
                Arguments.of(404, problem(404, "VIDEO_NOT_FOUND"), UNAVAILABLE),
                Arguments.of(200, "{\"items\":[{\"detail\":\"" + SECRET + "\"}]}", UNAVAILABLE),
                Arguments.of(503, problem(503, "VIDEO_LIBRARY_UNAVAILABLE"), LIBRARY_DOWN),
                Arguments.of(503, "", UNAVAILABLE));
    }

    @ParameterizedTest(name = "API {0} -> 503")
    @MethodSource("failures")
    @DisplayName("when the API or the library fails, the screen is a 503 with a notice and nothing of the answer")
    void unavailable(int status, String body, String message) throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "GET", API, status, body);

        MockHttpServletResponse response = open("ADMIN");
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(main).contains(message).doesNotContain("<table").doesNotContain("Todavía no hay")
                .contains("role=\"search\"");
    }

    @Test
    @DisplayName("a role the API refuses is a 403 notice, without the API's body")
    void accessDeniedByTheApi() throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "GET", API, 403, problem(403, "ACCESS_DENIED"));

        MockHttpServletResponse response = open("INSTRUCTOR");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(render(response)).contains("No podés gestionar los videos.");
    }

    // ---- notices, access and navigation ----

    @Test
    @DisplayName("the one-time notice of a rename is shown once, on the list the redirect lands on")
    void flashIsShownOnce() throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "PUT", API + "/" + VIDEO_ID, 200, "{}");
        stubList(page(0, 1, 1, video(VIDEO_ID, "Abrazo", "FINISHED", 30, null, true)));
        Cookie session = logIn("ADMIN");

        mockMvc.perform(post(LIST + "/" + VIDEO_ID + "/renombrar").cookie(session).with(csrf())
                .param("title", "Nuevo"));
        String first = mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse()
                .getContentAsString());
        String second = mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse()
                .getContentAsString());

        assertThat(first).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Video renombrado\\.");
        assertThat(second).doesNotContain("Video renombrado.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        MockHttpServletResponse response = open(role);

        assertThat(response.getStatus()).isEqualTo(403);
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @Test
    @DisplayName("an anonymous visitor goes to the login page")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(LIST)).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(LIST).with(csrf())).andExpect(redirectedUrlPattern("**/login"));

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR"})
    @DisplayName("the management navigation offers Videos to the administrator and the instructor, and marks it")
    void navigationOffersVideos(String role) throws Exception {
        stubList(page(0, 0, 0));

        String main = render(open(role));

        assertThat(main).containsPattern("<a[^>]*href=\"/gestion/videos\"[^>]*aria-current=\"page\"[^>]*>\\s*Videos")
                .contains("<h1>Videos</h1>");
    }
}
