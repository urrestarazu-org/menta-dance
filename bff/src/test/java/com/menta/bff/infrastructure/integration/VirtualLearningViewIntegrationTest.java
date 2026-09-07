package com.menta.bff.infrastructure.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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

    private static final String COURSE_DETAIL_BODY = """
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
                        .withBody("""
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
                        .withBody("""
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
                        .withBody("""
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
                        .withBody("""
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
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlEqualTo(streamUrl(PREMIUM_LESSON_ID))));
    }

    @Test
    @DisplayName("entitled student navigates via the upstream navigation block, not course-detail order")
    void entitledStudentRequest_shouldRenderPlayerWithUpstreamNavigation() throws Exception {
        String accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzdHVkZW50QGV4YW1wbGUuY29tIn0.signature";
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh-token-abc")
                        .withBody("""
                                {
                                  "access_token": "%s",
                                  "token_type": "Bearer",
                                  "expires_in": 3600
                                }
                                """.formatted(accessToken))));

        var loginResult = mockMvc.perform(post("/login")
                        .param("username", "student@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var sessionCookie = loginResult.getResponse().getCookie("SESSION");

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
                        .withBody("""
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
                        .withBody("""
                                {
                                  "stream": {
                                    "url": "%s",
                                    "expiresAt": "2026-01-01T00:00:00Z"
                                  }
                                }
                                """.formatted(PREMIUM_STREAM_URL))));

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
                        .withBody("""
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
                        .withBody("""
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
}
