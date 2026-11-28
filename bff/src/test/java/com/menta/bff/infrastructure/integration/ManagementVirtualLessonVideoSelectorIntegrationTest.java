package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.API;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.missing;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.page;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.problem;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.video;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The video selector of the lesson form (#843): the library list that replaces the free-text identifier, what each
 * option and the preview say, the warnings for a video that will not play, the fallback to a pasted identifier, and a
 * form that still saves when the video API is down. Each case logs in for real against the stubbed APIs.
 */
@DisplayName("Management virtual lesson video selector Integration Tests")
class ManagementVirtualLessonVideoSelectorIntegrationTest extends BaseIntegrationTest {

    private static final String ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String M1 = "11111111-1111-4111-8111-111111111111";
    private static final String L1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String L2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String CREATE = "/gestion/cursos-virtuales/" + ID + "/modulos/" + M1 + "/lecciones";
    private static final String NEW = CREATE + "/nueva";
    private static final String EDIT = CREATE + "/" + L1 + "/editar";
    private static final String EDIT_WITHOUT_VIDEO = CREATE + "/" + L2 + "/editar";
    private static final String STRUCTURE = "/gestion/cursos-virtuales/" + ID + "/estructura";
    private static final String COURSE_API = "/api/v1/admin/virtual/courses/" + ID;
    private static final String LESSONS_API = "/api/v1/admin/virtual/modules/" + M1 + "/lessons";
    private static final String LESSON_API = "/api/v1/admin/virtual/lessons/" + L1;

    private static final String READY = "ready-0001";
    private static final String NO_MP4 = "nomp4-0002";
    private static final String PROCESSING = "proc-0003";
    private static final String BROKEN = "error-0004";
    private static final String CURRENT = "current-0005";

    private static final String NO_VIDEO_NOTICE = "Esta lección no tiene video asociado por el momento.";
    private static final String LIST_DOWN = "No pudimos cargar la lista de videos. Podés guardar la lección igual: "
            + "pegá el identificador del video en el campo de abajo o dejalo vacío.";
    private static final String NO_MP4_WARNING = "Este video no se va a reproducir con la configuración actual del "
            + "CDN: no tiene MP4 de respaldo.";

    private Cookie logIn(String role) throws Exception {
        return VideoApiFixtures.logIn(mockMvc, WIRE_MOCK_SERVER, role);
    }

    private static String lesson(String id, String title, String videoId) {
        return "{\"lessonId\":\"%s\",\"title\":\"%s\",\"description\":\"Texto de la lección\",\"durationMinutes\":10,"
                .formatted(id, title) + "\"isFree\":false,\"order\":1,\"videoId\":%s}"
                .formatted(videoId == null ? "null" : "\"" + videoId + "\"");
    }

    /** A course whose first module has a lesson with the video {@code current} and one without video. */
    private static void stubCourse(String current) {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "GET", COURSE_API, 200,
                ("{\"courseId\":\"%s\",\"title\":\"Tango base\",\"shortDescription\":\"Corta\","
                        + "\"description\":\"Larga\",\"professorId\":\"%s\",\"thumbnailUrl\":\"https://img.example/t.png\","
                        + "\"category\":\"Tango\",\"level\":\"BEGINNER\",\"isPremium\":false,\"status\":\"DRAFT\","
                        + "\"modules\":[{\"moduleId\":\"%s\",\"title\":\"Pasos básicos\",\"preview\":false,\"order\":1,"
                        + "\"lessons\":[%s,%s]}],"
                        + "\"stats\":{\"moduleCount\":1,\"lessonCount\":2,\"totalDuration\":\"25m\"}}")
                        .formatted(ID, M1, M1, lesson(L1, "Primer paso", current), lesson(L2, "Segundo paso", null)));
    }

    private static void stubVideos(String... videos) {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "GET", API, 200, page(0, 1, videos.length, videos));
    }

    private static void stubLibrary() {
        stubVideos(video(READY, "Abrazo básico", "FINISHED", 65, "https://vz-1.b-cdn.net/ready/thumb.jpg", true),
                video(NO_MP4, "Giro sin copia", "FINISHED", 30, null, false),
                video(PROCESSING, "Giro nuevo", "PROCESSING", null, null, false),
                video(BROKEN, "Vals roto", "ERROR", null, null, false),
                video(CURRENT, "Video actual", "FINISHED", 90, "https://vz-1.b-cdn.net/current/thumb.jpg", true));
    }

    private static void stubVideosDown() {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "GET", API, 500, problem(500, "OTHER"));
    }

    private static void stubWrites() {
        String body = "{\"lessonId\":\"%s\",\"moduleId\":\"%s\",\"courseId\":\"" + ID + "\",\"title\":\"Primer paso\","
                + "\"description\":\"Texto\",\"videoId\":null,\"durationMinutes\":10,\"free\":false,\"order\":1}";
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "POST", LESSONS_API, 201, body.formatted(L1, M1));
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "PUT", LESSON_API, 200, body.formatted(L1, M1));
    }

    private String render(MockHttpServletRequestBuilder request) throws Exception {
        return render(request, 200);
    }

    private String render(MockHttpServletRequestBuilder request, int status) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsString()).doesNotContain("/api/v1").doesNotContain(VideoApiFixtures.SECRET);
        return mainOf(response.getContentAsString());
    }

    /** A valid submission of the form with the given fields added or replaced. */
    private static MockHttpServletRequestBuilder submit(String url, Cookie session, String... params) {
        Map<String, String> fields = new LinkedHashMap<>(Map.of("title", "Primer paso",
                "description", "Texto de la lección", "durationMinutes", "10"));
        for (int i = 0; i < params.length; i += 2) {
            fields.put(params[i], params[i + 1]);
        }
        MockHttpServletRequestBuilder request = post(url).cookie(session).with(csrf());
        fields.forEach(request::param);
        return request;
    }

    // ---- the selector: what it lists ----

    @Test
    @DisplayName("the new lesson form lists the library as a select whose options say title, state and MP4 in words")
    void newFormListsTheLibrary() throws Exception {
        stubLibrary();

        String main = render(get(NEW).cookie(logIn("ADMIN")));

        assertThat(main).containsPattern("<label[^>]*for=\"videoId\"[^>]*>\\s*Video de la lección")
                .containsPattern("<select[^>]*id=\"videoId\"[^>]*name=\"videoId\"")
                .containsPattern("<option value=\"\">\\s*Sin video por ahora\\s*</option>")
                .containsPattern("<option value=\"" + READY + "\"[^>]*>\\s*Abrazo básico — Listo, con MP4\\s*</option>")
                .containsPattern("<option value=\"" + NO_MP4 + "\"[^>]*>\\s*Giro sin copia — Listo, sin MP4 "
                        + "\\(no se va a reproducir\\)\\s*</option>")
                .containsPattern("<option value=\"" + PROCESSING + "\"[^>]*>\\s*Giro nuevo — Procesando, sin MP4 "
                        + "\\(todavía no se reproduce\\)\\s*</option>")
                .containsPattern("<option value=\"" + BROKEN + "\"[^>]*>\\s*Vals roto — Con error, sin MP4 "
                        + "\\(no se va a reproducir\\)\\s*</option>")
                .doesNotContain("selected=\"selected\"");
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlPathEqualTo(API)).withQueryParam("page", equalTo("0"))
                .withQueryParam("size", equalTo("100")).withoutQueryParam("search")
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN"))));
    }

    @Test
    @DisplayName("an instructor gets the same list: the BFF asks the API as the user and filters nothing")
    void instructorListsTheLibrary() throws Exception {
        stubLibrary();

        String main = render(get(NEW).cookie(logIn("INSTRUCTOR")));

        assertThat(main).contains("Abrazo básico — Listo, con MP4", "Giro nuevo");
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlPathEqualTo(API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("INSTRUCTOR"))));
    }

    @Test
    @DisplayName("a video that no longer exists is not offered, and the titles of the library are escaped")
    void doesNotOfferMissingAndEscapes() throws Exception {
        stubVideos(missing("gone-9"), video(READY, "<script>boom()</script>", "FINISHED", 10, null, true));

        String main = render(get(NEW).cookie(logIn("ADMIN")));

        assertThat(main).doesNotContain("gone-9").contains("&lt;script&gt;boom()&lt;/script&gt; — Listo, con MP4")
                .doesNotContain("<script>boom");
    }

    @Test
    @DisplayName("says so when the library has more videos than the list shows, and points to the pasted identifier")
    void saysWhenTheListIsCut() throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "GET", API, 200,
                page(0, 3, 250, video(READY, "Abrazo básico", "FINISHED", 65, null, true)));

        String main = render(get(NEW).cookie(logIn("ADMIN")));

        assertThat(main).contains("Se muestran los 100 videos más recientes de los 250 de la biblioteca. "
                + "Si no está el que buscás, pegá su identificador abajo.");
    }

    @Test
    @DisplayName("the full list shows no cut notice")
    void noCutNotice() throws Exception {
        stubLibrary();

        assertThat(render(get(NEW).cookie(logIn("ADMIN")))).doesNotContain("Se muestran los 100");
    }

    // ---- the current video on edit ----

    @Test
    @DisplayName("editing preselects the current video, does not offer to remove it and previews it")
    void editPreselectsTheCurrentVideo() throws Exception {
        stubCourse(CURRENT);
        stubLibrary();

        String main = render(get(EDIT).cookie(logIn("INSTRUCTOR")));

        assertThat(main).containsPattern("<option value=\"" + CURRENT + "\"[^>]*selected=\"selected\"[^>]*>"
                        + "\\s*Video actual — Listo, con MP4")
                .doesNotContain("Sin video por ahora").doesNotContain(NO_VIDEO_NOTICE)
                .contains("Si no elegís otro video, no se cambia el video asignado.")
                .containsPattern("<img[^>]*src=\"https://vz-1.b-cdn.net/current/thumb.jpg\"[^>]*alt=\"\"")
                .containsPattern("Video elegido:[\\s\\S]*?Video actual[\\s\\S]*?Listo, con MP4");
        assertThat(main.split("selected=\"selected\"", -1)).hasSize(2);
    }

    @Test
    @DisplayName("the current video stays selected even when the list does not have it, with a warning that says so")
    void currentVideoNotInTheList() throws Exception {
        stubCourse("old-video-77");
        stubLibrary();

        String main = render(get(EDIT).cookie(logIn("ADMIN")));

        assertThat(main).containsPattern("<option value=\"old-video-77\"[^>]*selected=\"selected\"[^>]*>\\s*Video "
                        + "old-video-77 — no está en la lista")
                .contains("No encontramos este video en la lista de la biblioteca, así que no podemos asegurar "
                        + "que se reproduzca.");
    }

    @Test
    @DisplayName("a current video that no longer exists is selected and warned about, with a text notice, no image")
    void currentVideoMissingInLibrary() throws Exception {
        stubCourse("gone-9");
        stubVideos(missing("gone-9"), video(READY, "Abrazo básico", "FINISHED", 65, null, true));

        String main = render(get(EDIT).cookie(logIn("INSTRUCTOR")));

        assertThat(main).containsPattern("<option value=\"gone-9\"[^>]*selected=\"selected\"[^>]*>"
                        + "\\s*Video gone-9 — ya no existe en la biblioteca")
                .contains("Este video ya no existe en la biblioteca, así que no se va a reproducir. Elegí otro.")
                .contains("Este video ya no existe en la biblioteca.").doesNotContain("<img");
    }

    // ---- warnings: the chosen video will not play ----

    static Stream<Arguments> unplayable() {
        return Stream.of(
                Arguments.of(NO_MP4, NO_MP4_WARNING),
                Arguments.of(PROCESSING, "Este video todavía se está procesando, así que no se va a reproducir hasta "
                        + "que termine."),
                Arguments.of(BROKEN, "Este video tiene un error, así que no se va a reproducir."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unplayable")
    @DisplayName("a video that will not play is warned about in visible text, as a status message, next to the select")
    void warnsWhenItWillNotPlay(String videoId, String warning) throws Exception {
        stubCourse(videoId);
        stubLibrary();

        String main = render(get(EDIT).cookie(logIn("ADMIN")));

        assertThat(main).containsPattern("<div[^>]*class=\"[^\"]*alert-warning[^\"]*\"[^>]*role=\"status\"[^>]*>\\s*<p>"
                + warning.replace(".", "\\.") + "</p>");
        assertThat(main).containsPattern("<select[^>]*aria-describedby=\"[^\"]*videoId-status");
    }

    @Test
    @DisplayName("a video that plays has no warning")
    void noWarningWhenItPlays() throws Exception {
        stubCourse(READY);
        stubLibrary();

        String main = render(get(EDIT).cookie(logIn("ADMIN")));

        assertThat(main).doesNotContain("alert-warning").doesNotContain("no se va a reproducir con la configuración");
    }

    @Test
    @DisplayName("choosing an unplayable video and failing validation shows its warning again, with the pick kept")
    void warningAfterAFailedSubmit() throws Exception {
        stubLibrary();

        String main = render(submit(CREATE, logIn("ADMIN"), "title", "", "videoId", NO_MP4), 200);

        assertThat(main).containsPattern("<option value=\"" + NO_MP4 + "\"[^>]*selected=\"selected\"")
                .contains(NO_MP4_WARNING).contains("Ingresá el título de la lección.");
    }

    // ---- a lesson without a video ----

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {EDIT_WITHOUT_VIDEO, NEW})
    @DisplayName("a lesson with no video shows the notice where the preview goes")
    void noVideoNotice(String url) throws Exception {
        stubCourse(CURRENT);
        stubLibrary();

        String main = render(get(url).cookie(logIn("ADMIN")));

        assertThat(main).containsPattern("<div[^>]*class=\"[^\"]*preview-box preview-box-warning[^\"]*\"[^>]*>"
                + "[\\s\\S]*?<p>" + NO_VIDEO_NOTICE.replace(".", "\\.") + "</p>");
        assertThat(main).doesNotContain("Video elegido:");
    }

    @Test
    @DisplayName("editing a lesson without video offers the empty option, since nothing is assigned yet")
    void editWithoutVideoOffersTheEmptyOption() throws Exception {
        stubCourse(CURRENT);
        stubLibrary();

        String main = render(get(EDIT_WITHOUT_VIDEO).cookie(logIn("ADMIN")));

        assertThat(main).containsPattern("<option value=\"\">\\s*Sin video por ahora\\s*</option>");
    }

    // ---- the pasted identifier: the fallback ----

    @Test
    @DisplayName("below the selector there is a collapsed disclosure with the text field for a pasted identifier")
    void pasteFallbackIsCollapsed() throws Exception {
        stubLibrary();

        String main = render(get(NEW).cookie(logIn("ADMIN")));

        assertThat(main).containsPattern("<details(?![^>]*open)[^>]*>\\s*<summary[^>]*>"
                        + "\\s*Pegar un identificador\\s*</summary>")
                .containsPattern("<input[^>]*id=\"videoIdManual\"[^>]*name=\"videoIdManual\"")
                .doesNotContain("open=\"open\"");
        assertThat(main.indexOf("<select")).isLessThan(main.indexOf("<details"));
    }

    @Test
    @DisplayName("a pasted identifier is what is saved, even when a video is picked, and it is trimmed")
    void pastedIdentifierWins() throws Exception {
        stubWrites();
        Cookie session = logIn("INSTRUCTOR");

        mockMvc.perform(submit(CREATE, session, "videoId", READY, "videoIdManual", "  pasted-123 "))
                .andExpect(flash().attribute("structureAlert", "Lección creada."));

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(LESSONS_API)).withRequestBody(equalToJson(
                "{\"title\":\"Primer paso\",\"description\":\"Texto de la lección\",\"videoId\":\"pasted-123\","
                        + "\"durationMinutes\":10,\"free\":false}")));
    }

    @Test
    @DisplayName("an invalid pasted identifier is reported next to its field, which opens its disclosure")
    void invalidPastedIdentifier() throws Exception {
        stubLibrary();

        String main = render(submit(CREATE, logIn("ADMIN"), "videoIdManual", "dos palabras"), 200);

        assertThat(main).contains("El identificador del video no puede tener espacios.", "Hay 1 error en el formulario")
                .contains("href=\"#videoIdManual\"").containsPattern("<details[^>]*open=\"open\"")
                .containsPattern("<input[^>]*id=\"videoIdManual\"[^>]*value=\"dos palabras\"");
    }

    // ---- saving the selection ----

    @Test
    @DisplayName("creating with a picked video sends its identifier, and the video API is not asked to save")
    void createWithAPickedVideo() throws Exception {
        stubWrites();

        mockMvc.perform(submit(CREATE, logIn("ADMIN"), "videoId", READY))
                .andExpect(redirectedUrl(STRUCTURE + "?modulo=" + M1));

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(LESSONS_API)).withRequestBody(equalToJson(
                "{\"title\":\"Primer paso\",\"description\":\"Texto de la lección\",\"videoId\":\"" + READY
                        + "\",\"durationMinutes\":10,\"free\":false}")));
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(API)));
    }

    @Test
    @DisplayName("editing with another picked video sends its identifier")
    void editWithAPickedVideo() throws Exception {
        stubWrites();

        mockMvc.perform(submit(EDIT, logIn("INSTRUCTOR"), "videoId", PROCESSING))
                .andExpect(redirectedUrl(STRUCTURE + "?modulo=" + M1))
                .andExpect(flash().attribute("structureAlert", "Lección actualizada."));

        WIRE_MOCK_SERVER.verify(1, putRequestedFor(urlEqualTo(LESSON_API)).withRequestBody(equalToJson(
                "{\"title\":\"Primer paso\",\"description\":\"Texto de la lección\",\"videoId\":\"" + PROCESSING
                        + "\",\"durationMinutes\":10,\"free\":false}")));
    }

    @Test
    @DisplayName("editing with no video field sends no video, so the assigned one is not changed; creating sends none")
    void emptyKeepsTheVideo() throws Exception {
        stubWrites();
        Cookie session = logIn("ADMIN");

        mockMvc.perform(submit(EDIT, session, "videoId", "", "videoIdManual", ""));
        mockMvc.perform(submit(CREATE, session));

        WIRE_MOCK_SERVER.verify(1, putRequestedFor(urlEqualTo(LESSON_API)).withRequestBody(equalToJson(
                "{\"title\":\"Primer paso\",\"description\":\"Texto de la lección\",\"durationMinutes\":10,"
                        + "\"free\":false}")));
        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(LESSONS_API)).withRequestBody(equalToJson(
                "{\"title\":\"Primer paso\",\"description\":\"Texto de la lección\",\"durationMinutes\":10,"
                        + "\"free\":false}")));
    }

    // ---- the video API is down ----

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {NEW, EDIT})
    @DisplayName("without the video API the form still renders, with a clear warning and the text field expanded")
    void formWithoutTheVideoApi(String url) throws Exception {
        stubCourse(CURRENT);
        stubVideosDown();

        String main = render(get(url).cookie(logIn("ADMIN")));

        assertThat(main).contains(LIST_DOWN).containsPattern("<details[^>]*open=\"open\"")
                .containsPattern("<input[^>]*id=\"videoIdManual\"[^>]*name=\"videoIdManual\"")
                .doesNotContain("<select").contains("Cancelar").containsPattern("<button type=\"submit\"")
                .doesNotContain("Video elegido:");
        assertThat(main).containsPattern("<div[^>]*class=\"[^\"]*alert[^\"]*\"[^>]*role=\"(status|alert)\"[^>]*>\\s*<p>"
                + LIST_DOWN.replace(".", "\\.") + "</p>");
    }

    @Test
    @DisplayName("without the video API, editing shows the current identifier in the text field so saving keeps it")
    void editWithoutTheVideoApiKeepsTheCurrentIdentifier() throws Exception {
        stubCourse(CURRENT);
        stubVideosDown();

        String main = render(get(EDIT).cookie(logIn("ADMIN")));

        assertThat(main).containsPattern("<input[^>]*id=\"videoIdManual\"[^>]*value=\"" + CURRENT + "\"")
                .contains("Si lo dejás vacío no se cambia el video asignado.");
    }

    @Test
    @DisplayName("without the video API, a lesson without video still shows the notice")
    void noVideoNoticeWithoutTheVideoApi() throws Exception {
        stubCourse(CURRENT);
        stubVideosDown();

        assertThat(render(get(EDIT_WITHOUT_VIDEO).cookie(logIn("ADMIN")))).contains(NO_VIDEO_NOTICE);
    }

    @ParameterizedTest(name = "video API answers {0}")
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    @DisplayName("whatever the video API answers, the form renders with the warning and no detail of the API")
    void anyFailureOfTheVideoApi(int status) throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "GET", API, status, problem(status, "ACCESS_DENIED"));

        String main = render(get(NEW).cookie(logIn("ADMIN")));

        assertThat(main).contains(LIST_DOWN);
    }

    @Test
    @DisplayName("the lesson is saved with its other fields while the video API is down, with a pasted identifier")
    void savesWhileTheVideoApiIsDown() throws Exception {
        stubWrites();
        stubVideosDown();
        Cookie session = logIn("ADMIN");

        mockMvc.perform(submit(CREATE, session, "videoIdManual", "pasted-9"))
                .andExpect(redirectedUrl(STRUCTURE + "?modulo=" + M1));
        mockMvc.perform(submit(EDIT, session, "videoIdManual", "pasted-9", "free", "on"))
                .andExpect(redirectedUrl(STRUCTURE + "?modulo=" + M1));

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(LESSONS_API)).withRequestBody(equalToJson(
                "{\"title\":\"Primer paso\",\"description\":\"Texto de la lección\",\"videoId\":\"pasted-9\","
                        + "\"durationMinutes\":10,\"free\":false}")));
        WIRE_MOCK_SERVER.verify(1, putRequestedFor(urlEqualTo(LESSON_API)).withRequestBody(equalToJson(
                "{\"title\":\"Primer paso\",\"description\":\"Texto de la lección\",\"videoId\":\"pasted-9\","
                        + "\"durationMinutes\":10,\"free\":true}")));
    }

    @Test
    @DisplayName("a failed save keeps what was typed and shows the form with the list again")
    void failedSaveKeepsTheForm() throws Exception {
        stubLibrary();
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "POST", LESSONS_API, 400, problem(400, "INVALID_REQUEST"));

        String main = render(submit(CREATE, logIn("ADMIN"), "videoId", PROCESSING), 400);

        assertThat(main).contains("Revisá los datos ingresados.")
                .containsPattern("<option value=\"" + PROCESSING + "\"[^>]*selected=\"selected\"");
    }

    // ---- access ----

    @Test
    @DisplayName("the video API is never asked on a refused role")
    void refusedRolesNeverAskTheVideoApi() throws Exception {
        stubLibrary();

        mockMvc.perform(get(NEW).cookie(logIn("STUDENT"))).andReturn();

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(API)));
    }
}
