package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full-stack rendering coverage for the course-detail view (spec
 * {@code virtual-course-detail-view}), asserting on actual rendered content
 * against a WireMock-stubbed catalog upstream — distinct from
 * {@link VirtualLearningSecurityIntegrationTest}, which only asserts on
 * reachability/redirection.
 */
@DisplayName("Virtual Learning View Integration Tests")
class VirtualLearningViewIntegrationTest extends BaseIntegrationTest {

    private static final String COURSE_ID = "course-1";
    private static final String COURSE_DETAIL_URL = "/api/v1/catalog/courses/" + COURSE_ID;

    private static final String COURSE_DETAIL_BODY =
            """
            {
              "courseId": "course-1",
              "title": "Ballet Básico",
              "description": "Introducción al ballet clásico",
              "thumbnailUrl": "https://cdn/thumb.jpg",
              "category": "ballet",
              "level": "beginner",
              "isPremium": true,
              "modules": [
                {
                  "moduleId": "module-1",
                  "title": "Módulo 1",
                  "order": 1,
                  "lessons": [
                    {
                      "lessonId": "lesson-1",
                      "title": "Postura básica",
                      "duration": "05:00",
                      "isFree": true,
                      "order": 1
                    },
                    {
                      "lessonId": "lesson-2",
                      "title": "Giro avanzado",
                      "duration": "08:00",
                      "isFree": false,
                      "order": 2
                    }
                  ]
                }
              ],
              "stats": {
                "moduleCount": 1,
                "lessonCount": 2,
                "totalDuration": "13m"
              }
            }
            """;

    @Test
    @DisplayName("anonymous course-detail request renders free/premium markers correctly")
    void anonymousCourseDetailRequest_shouldRenderFreePremiumMarkers() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(COURSE_DETAIL_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(COURSE_DETAIL_BODY)));

        var result = mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Ballet Básico");
        assertThat(body).contains("Postura básica");
        assertThat(body).contains("Giro avanzado");
        // Lesson links carry both ids, per D1 (nested route for lesson deep links)
        assertThat(body).contains("/courses/course-1/lessons/lesson-1");
        assertThat(body).contains("/courses/course-1/lessons/lesson-2");
        // Free/premium markers are distinct per lesson
        assertThat(body).contains("Gratis");
        assertThat(body).contains("Restringida");
    }

    @Test
    @DisplayName("catalog 404 renders the error view with no problem-detail body")
    void catalogNotFound_shouldRenderErrorViewWithoutProblemDetailBody() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(COURSE_DETAIL_URL))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(
                                """
                                {"type":"about:blank","title":"Course not found","status":404}
                                """)));

        var result = mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isNotFound())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("about:blank");
        assertThat(body).doesNotContain("Course not found");
        assertThat(body).doesNotContain("application/problem+json");
    }

    @Test
    @DisplayName("catalog 503 with Retry-After renders the error view without exposing the upstream hint")
    void catalogUnavailable_shouldRenderErrorViewWithoutRetryAfterLeak() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(COURSE_DETAIL_URL))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "30")
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(
                                """
                                {"type":"about:blank","title":"Service unavailable","status":503}
                                """)));

        var result = mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        assertThat(result.getResponse().getHeader("Retry-After")).isNull();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("about:blank");
        assertThat(body).doesNotContain("Service unavailable");
    }

    // --- Lesson view (#170, PR 3b) ---------------------------------------

    private static final String FREE_LESSON_ID = "lesson-1";
    private static final String PREMIUM_LESSON_ID = "lesson-2";
    private static final String FREE_STREAM_URL = "https://cdn.mentadance.test/stream/lesson-1.m3u8";
    private static final String PREMIUM_STREAM_URL = "https://cdn.mentadance.test/stream/lesson-2.m3u8";

    private String lessonUrl(String lessonId) {
        return "/api/v1/virtual/lessons/" + lessonId;
    }

    private String streamUrl(String lessonId) {
        return "/api/v1/virtual/lessons/" + lessonId + "/stream";
    }

    private void stubCourseDetail() {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(COURSE_DETAIL_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(COURSE_DETAIL_BODY)));
    }

    @Test
    @DisplayName("anonymous visitor plays a free lesson: player renders with a non-empty stream URL")
    void anonymousFreeLessonRequest_shouldRenderPlayerWithStreamUrl() throws Exception {
        stubCourseDetail();
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(lessonUrl(FREE_LESSON_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "lesson": {
                                    "lessonId": "lesson-1",
                                    "title": "Postura básica",
                                    "description": "Introducción a la postura",
                                    "duration": "05:00",
                                    "order": 1,
                                    "videoId": null,
                                    "course": {"courseId": "course-1", "title": "Ballet Básico"},
                                    "module": {"moduleId": "module-1", "title": "Módulo 1"}
                                  },
                                  "navigation": {
                                    "previousLesson": null,
                                    "nextLesson": {"lessonId": "lesson-2", "title": "Giro avanzado"}
                                  }
                                }
                                """)));
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(streamUrl(FREE_LESSON_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "stream": {
                                    "url": "%s",
                                    "expiresAt": "2026-01-01T00:00:00Z"
                                  }
                                }
                                """.formatted(FREE_STREAM_URL))));

        var result = mockMvc.perform(get("/courses/{courseId}/lessons/{lessonId}", COURSE_ID, FREE_LESSON_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("lesson"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Postura básica");
        assertThat(body).contains(FREE_STREAM_URL);
    }

    @Test
    @DisplayName("non-entitled visitor on a premium lesson: sample view, no videoId/stream URL, zero stream calls")
    void nonEntitledPremiumLessonRequest_shouldRenderSampleWithNoStreamLeak() throws Exception {
        stubCourseDetail();
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(lessonUrl(PREMIUM_LESSON_ID)))
                .willReturn(aResponse().withStatus(403)));

        var result = mockMvc.perform(get("/courses/{courseId}/lessons/{lessonId}", COURSE_ID, PREMIUM_LESSON_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("lesson"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Giro avanzado");
        // The most important full-stack assertion in this change: not a single
        // videoId or stream-URL-shaped string reaches the rendered response for
        // a non-entitled visitor, and the BFF never even called /stream.
        assertThat(body).doesNotContainIgnoringCase("videoId");
        assertThat(body).doesNotContain(".m3u8");
        assertThat(body).doesNotContainIgnoringCase("streamUrl");
        assertThat(body).doesNotContain("<video");
        // #177: the subscription CTA is a real link to the plans page, not
        // the dead-end message that predated it (spec virtual-lesson-view,
        // "Subscription CTA links to the plans page").
        assertThat(body).contains("href=\"/plans\"");
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlEqualTo(streamUrl(PREMIUM_LESSON_ID))));
    }

    @Test
    @DisplayName("entitled student navigates via the upstream navigation block, not course-detail order")
    void entitledStudentRequest_shouldRenderPlayerWithUpstreamNavigation() throws Exception {
        String accessToken = TestAccessTokens.withRole("STUDENT");
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh-token-abc")
                        .withBody(
                                """
                                {
                                  "access_token": "%s",
                                  "token_type": "Bearer",
                                  "expires_in": 3600
                                }
                                """.formatted(accessToken))));

        stubCourseDetail();
        // The upstream navigation block deliberately differs from course-detail
        // order (which would derive "lesson-1"/"Postura básica" as previous):
        // this proves nav renders from the lesson endpoint's own block (spec
        // "Previous/next lesson navigation" — "Navigation from the upstream
        // block when access is granted"), not re-derived from course order.
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(lessonUrl(PREMIUM_LESSON_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "lesson": {
                                    "lessonId": "lesson-2",
                                    "title": "Giro avanzado",
                                    "description": "Técnica de giro",
                                    "duration": "08:00",
                                    "order": 2,
                                    "videoId": "video-abc123",
                                    "course": {"courseId": "course-1", "title": "Ballet Básico"},
                                    "module": {"moduleId": "module-1", "title": "Módulo 1"}
                                  },
                                  "navigation": {
                                    "previousLesson": {"lessonId": "lesson-0", "title": "Calentamiento"},
                                    "nextLesson": null
                                  }
                                }
                                """)));
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(streamUrl(PREMIUM_LESSON_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "stream": {
                                    "url": "%s",
                                    "expiresAt": "2026-01-01T00:00:00Z"
                                  }
                                }
                                """.formatted(PREMIUM_STREAM_URL))));

        var loginResult = mockMvc.perform(post("/login")
                        .param("email", "student@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var sessionCookie = loginResult.getResponse().getCookie("SESSION");
        var result = mockMvc.perform(get("/courses/{courseId}/lessons/{lessonId}", COURSE_ID, PREMIUM_LESSON_ID)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("lesson"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(PREMIUM_STREAM_URL);
        assertThat(body).contains("Calentamiento");
    }

    @Test
    @DisplayName("stream 503 after a granted lesson renders the error view, never a partial player")
    void streamUnavailableAfterGrantedLesson_shouldRenderErrorViewWithoutPartialPlayer() throws Exception {
        stubCourseDetail();
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(lessonUrl(FREE_LESSON_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "lesson": {
                                    "lessonId": "lesson-1",
                                    "title": "Postura básica",
                                    "description": "Introducción a la postura",
                                    "duration": "05:00",
                                    "order": 1,
                                    "videoId": null,
                                    "course": {"courseId": "course-1", "title": "Ballet Básico"},
                                    "module": {"moduleId": "module-1", "title": "Módulo 1"}
                                  },
                                  "navigation": {
                                    "previousLesson": null,
                                    "nextLesson": null
                                  }
                                }
                                """)));
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(streamUrl(FREE_LESSON_ID)))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "30")
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(
                                """
                                {"type":"about:blank","title":"Service unavailable","status":503}
                                """)));

        var result = mockMvc.perform(get("/courses/{courseId}/lessons/{lessonId}", COURSE_ID, FREE_LESSON_ID))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("<video");
        assertThat(body).doesNotContain("about:blank");
        assertThat(body).doesNotContain("Service unavailable");
    }

    // --- Course-detail progress-aware CTA (#58, PR 3) --------------------

    private String progressUrl(String courseId) {
        return "/api/v1/virtual/courses/" + courseId + "/progress";
    }

    /**
     * Reuses the login→session-cookie flow already proven by {@code
     * entitledStudentRequest_shouldRenderPlayerWithUpstreamNavigation}
     * (lines 234-257), factored out for the progress-aware cases below.
     */
    private Cookie loginAndGetSessionCookie() throws Exception {
        String accessToken = TestAccessTokens.withRole("STUDENT");
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh-token-abc")
                        .withBody(
                                """
                                {
                                  "access_token": "%s",
                                  "token_type": "Bearer",
                                  "expires_in": 3600
                                }
                                """.formatted(accessToken))));

        var loginResult = mockMvc.perform(post("/login")
                        .param("email", "student@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return loginResult.getResponse().getCookie("SESSION");
    }

    @Test
    @DisplayName(
        "entitled student with resumable progress: renders Continuar with exact data and a query/fragment-free link")
    void entitledStudentWithProgress_shouldRenderContinuarWithExactDataAndCleanLink() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();
        stubCourseDetail();
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(progressUrl(COURSE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "courseId": "course-1",
                                  "completedLessons": 1,
                                  "totalLessons": 2,
                                  "percentage": 50,
                                  "resumeLesson": {
                                    "lessonId": "lesson-2",
                                    "moduleId": "module-1",
                                    "positionSeconds": 45,
                                    "completed": false
                                  }
                                }
                                """)));

        var result = mockMvc.perform(get("/courses/{courseId}", COURSE_ID).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("course-detail"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Continuar");
        assertThat(body).contains("Giro avanzado");
        assertThat(body).contains(">1<");
        assertThat(body).contains(">2<");
        assertThat(body).contains(">50<");
        assertThat(body).contains("href=\"/courses/course-1/lessons/lesson-2\"");
        // Locked D1/D4: no seek/position hint, no query string or fragment on the link.
        assertThat(body).doesNotContain("lessons/lesson-2?");
        assertThat(body).doesNotContain("lessons/lesson-2#");
    }

    @Test
    @DisplayName("entitled student with zero progress: renders Comenzar, never Continuar or a percentage")
    void entitledStudentWithZeroProgress_shouldRenderComenzarNotContinuar() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();
        stubCourseDetail();
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(progressUrl(COURSE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "courseId": "course-1",
                                  "completedLessons": 0,
                                  "totalLessons": 2,
                                  "percentage": 0,
                                  "resumeLesson": null
                                }
                                """)));

        var result = mockMvc.perform(get("/courses/{courseId}", COURSE_ID).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("course-detail"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Comenzar");
        assertThat(body).doesNotContain("Continuar");
    }

    @Test
    @DisplayName("403 on the progress call renders byte-identical to the anonymous rendering")
    void progressForbidden_shouldRenderByteEquivalentToAnonymous() throws Exception {
        stubCourseDetail();
        var anonymousResult = mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isOk())
                .andReturn();
        String anonymousBody = anonymousResult.getResponse().getContentAsString();

        Cookie sessionCookie = loginAndGetSessionCookie();
        stubCourseDetail();
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(progressUrl(COURSE_ID)))
                .willReturn(aResponse().withStatus(403)));

        var authenticatedResult = mockMvc.perform(get("/courses/{courseId}", COURSE_ID).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();
        String authenticatedBody = authenticatedResult.getResponse().getContentAsString();

        assertThat(mainOf(authenticatedBody)).isEqualTo(mainOf(anonymousBody));
    }

    @Test
    @DisplayName("anonymous request never calls the progress endpoint")
    void anonymousRequest_shouldNeverCallProgressEndpoint() throws Exception {
        stubCourseDetail();

        mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isOk());

        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlEqualTo(progressUrl(COURSE_ID))));
    }

    @Test
    @DisplayName("progress 503 still renders the catalog page with 200, no problem-detail leak and no error view")
    void progressUnavailable_shouldStillRenderCatalogPageWithoutLeakingUpstreamError() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();
        stubCourseDetail();
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(progressUrl(COURSE_ID)))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "30")
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(
                                """
                                {"type":"about:blank","title":"Service unavailable","status":503}
                                """)));

        var result = mockMvc.perform(get("/courses/{courseId}", COURSE_ID).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("course-detail"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Retry-After")).isNull();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Ballet Básico");
        assertThat(body).doesNotContain("about:blank");
        assertThat(body).doesNotContain("application/problem+json");
        assertThat(body).doesNotContain("Service unavailable");
        assertThat(body).contains("Comenzar");
    }

    // --- Lessons without video, already hidden by the API (#852, after #851) ---------

    private static String courseDetailBody(String stats, String... modules) {
        return ("{\"courseId\":\"course-1\",\"title\":\"Ballet Básico\","
                + "\"description\":\"Introducción al ballet clásico\",\"thumbnailUrl\":\"https://cdn/thumb.jpg\","
                + "\"category\":\"ballet\",\"level\":\"beginner\",\"isPremium\":true,\"modules\":[%s],"
                + "\"stats\":%s}").formatted(String.join(",", modules), stats);
    }

    private static String moduleJson(String moduleId, String title, int order, String... lessons) {
        return "{\"moduleId\":\"%s\",\"title\":\"%s\",\"order\":%d,\"lessons\":[%s]}"
                .formatted(moduleId, title, order, String.join(",", lessons));
    }

    private static String lessonJson(String lessonId, String title, int order) {
        return "{\"lessonId\":\"%s\",\"title\":\"%s\",\"duration\":\"05:00\",\"isFree\":false,\"order\":%d}"
                .formatted(lessonId, title, order);
    }

    private void stubCourseDetailBody(String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(COURSE_DETAIL_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    @DisplayName("course whose lessons the API already filtered: only those are listed, with the API's own counts")
    void courseWithLessonsHiddenByTheApi_shouldRenderOnlyListedLessonsWithApiCounts() throws Exception {
        // The API dropped "lesson-sin-video" and the module that was left empty ("module-vacio"): the BFF must
        // neither know about them nor recount. The stats are deliberately not derivable from the lists.
        stubCourseDetailBody(courseDetailBody(
                "{\"moduleCount\":2,\"lessonCount\":3,\"totalDuration\":\"1h 17m\"}",
                moduleJson("module-1", "Módulo 1", 1, lessonJson("lesson-1", "Postura básica", 1)),
                moduleJson("module-3", "Módulo 3", 3, lessonJson("lesson-4", "Giro doble", 1),
                        lessonJson("lesson-5", "Salto", 2))));

        String body = mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Módulo 1", "Módulo 3", "Postura básica", "Giro doble", "Salto");
        assertThat(body).contains("2 módulos", "3 lecciones", "1h 17m");
        assertThat(body).doesNotContain("lesson-sin-video").doesNotContain("module-vacio");
        assertThat(body).doesNotContain("/lessons/lesson-2").doesNotContain("/lessons/lesson-3");
        assertThat(body).doesNotContain("/api/v1");
    }

    @Test
    @DisplayName("course with a single visible lesson renders it, the counts and a working Continuar")
    void courseWithSingleVisibleLesson_shouldRenderItAndContinuar() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();
        stubCourseDetailBody(courseDetailBody(
                "{\"moduleCount\":1,\"lessonCount\":1,\"totalDuration\":\"5m\"}",
                moduleJson("module-1", "Módulo 1", 1, lessonJson("lesson-1", "Postura básica", 1))));
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(progressUrl(COURSE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "courseId": "course-1",
                                  "completedLessons": 0,
                                  "totalLessons": 1,
                                  "percentage": 0,
                                  "resumeLesson": {
                                    "lessonId": "lesson-1",
                                    "moduleId": "module-1",
                                    "positionSeconds": 12,
                                    "completed": false
                                  }
                                }
                                """)));

        String body = mockMvc.perform(get("/courses/{courseId}", COURSE_ID).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("1 módulos", "1 lecciones", "Postura básica");
        assertThat(body).contains("Continuar");
        assertThat(body).contains("href=\"/courses/course-1/lessons/lesson-1\"");
    }

    @Test
    @DisplayName("course left without modules renders without lesson links and offers Comenzar, never a crash")
    void courseWithoutVisibleModules_shouldRenderWithoutLessonLinks() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();
        stubCourseDetailBody(courseDetailBody("{\"moduleCount\":0,\"lessonCount\":0,\"totalDuration\":\"0m\"}"));
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(progressUrl(COURSE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "courseId": "course-1",
                                  "completedLessons": 0,
                                  "totalLessons": 0,
                                  "percentage": 0,
                                  "resumeLesson": null
                                }
                                """)));

        String body = mockMvc.perform(get("/courses/{courseId}", COURSE_ID).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("course-detail"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Ballet Básico", "0 módulos", "0 lecciones", "Comenzar");
        assertThat(body).doesNotContain("Continuar").doesNotContain("/lessons/");
    }

    @Test
    @DisplayName("resume lesson the course list no longer carries: no Continuar and no link to it")
    void resumeLessonAbsentFromTheListedLessons_shouldNotLinkToIt() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();
        stubCourseDetailBody(courseDetailBody(
                "{\"moduleCount\":1,\"lessonCount\":1,\"totalDuration\":\"5m\"}",
                moduleJson("module-1", "Módulo 1", 1, lessonJson("lesson-1", "Postura básica", 1))));
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(progressUrl(COURSE_ID)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "courseId": "course-1",
                                  "completedLessons": 0,
                                  "totalLessons": 1,
                                  "percentage": 0,
                                  "resumeLesson": {
                                    "lessonId": "lesson-sin-video",
                                    "moduleId": "module-1",
                                    "positionSeconds": 0,
                                    "completed": false
                                  }
                                }
                                """)));

        String body = mockMvc.perform(get("/courses/{courseId}", COURSE_ID).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Continuar").doesNotContain("lesson-sin-video");
        assertThat(body).contains("Comenzar", "Postura básica");
    }

    @Test
    @DisplayName("lesson without video: the API's 404 LESSON_NOT_FOUND renders the 404 page, no stream call, no leaks")
    void lessonWithoutVideo_shouldRenderTheNotFoundPageWithoutLeakingTheApiProblem() throws Exception {
        Cookie sessionCookie = loginAndGetSessionCookie();
        stubCourseDetail();
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(lessonUrl("lesson-sin-video")))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(
                                """
                                {
                                  "type": "about:blank",
                                  "title": "Not Found",
                                  "status": 404,
                                  "detail": "Lección no encontrada.",
                                  "code": "LESSON_NOT_FOUND"
                                }
                                """)));

        var anonymous = mockMvc.perform(get("/courses/{courseId}/lessons/{lessonId}", COURSE_ID, "lesson-sin-video"))
                .andExpect(status().isNotFound())
                .andReturn();
        var authenticated = mockMvc.perform(get("/courses/{courseId}/lessons/{lessonId}", COURSE_ID,
                        "lesson-sin-video").cookie(sessionCookie))
                .andExpect(status().isNotFound())
                .andReturn();

        for (var result : new MvcResult[] {anonymous, authenticated}) {
            // MockMvc does not run the container's error dispatch, so the /error page is requested with the
            // status and the exception the real request ended with, exactly as the servlet container would.
            assertThat(result.getResponse().getContentAsString()).isEmpty();
            String body = mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                            .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, result.getResponse().getStatus())
                            .requestAttr(RequestDispatcher.ERROR_EXCEPTION, result.getResolvedException()))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).contains("No encontramos la página que buscás", "Código: 404");
            assertThat(body).doesNotContain("/api/v1").doesNotContain("Lección no encontrada")
                    .doesNotContain("LESSON_NOT_FOUND").doesNotContain("about:blank")
                    .doesNotContain("application/problem+json");
        }
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlEqualTo(streamUrl("lesson-sin-video"))));
    }
}
