package com.menta.bff.infrastructure.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-regression coverage for #170's two new anonymous-reachable routes
 * (spec {@code virtual-api-integration}, "Anonymous-reachable course and
 * lesson routes" and "The new permitAll entries widen access to nothing
 * else"), extended by #177's {@code GET /plans} route (spec {@code
 * bff-plans-view}, "Plans-list route is permitted without widening access
 * elsewhere").
 * <p>
 * Both directions are exercised in the same class: new routes must be
 * anonymously reachable, AND every previously-protected route must still
 * redirect — a matcher tested only positively can fail open silently and no
 * error would ever surface it. Every new anonymous-reachable route's
 * both-directions coverage is added here rather than split into a
 * per-feature security test class.
 * </p>
 */
@DisplayName("Virtual Learning Security Integration Tests")
class VirtualLearningSecurityIntegrationTest extends BaseIntegrationTest {

    private static final String COURSE_ID = "course-1";
    private static final String LESSON_ID = "lesson-1";

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
                    }
                  ]
                }
              ],
              "stats": {
                "moduleCount": 1,
                "lessonCount": 1,
                "totalDuration": "5m"
              }
            }
            """;

    private static final String LESSON_BODY = """
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
            """;

    private static final String STREAM_BODY = """
            {
              "stream": {
                "url": "https://cdn/stream.m3u8",
                "expiresAt": "2026-01-01T00:00:00Z"
              }
            }
            """;

    private void stubCourseDetail() {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo("/api/v1/catalog/courses/" + COURSE_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(COURSE_DETAIL_BODY)));
    }

    private void stubGrantedLesson() {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo("/api/v1/virtual/lessons/" + LESSON_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(LESSON_BODY)));
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo("/api/v1/virtual/lessons/" + LESSON_ID + "/stream"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(STREAM_BODY)));
    }

    @Test
    @DisplayName("anonymous GET /courses/{courseId} returns 200 with no redirect to /login")
    void anonymousCourseDetailRequest_shouldReturn200_withNoLoginRedirect() throws Exception {
        stubCourseDetail();

        mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("anonymous GET /dashboard still redirects to /login, exactly as before this change")
    void anonymousDashboardRequest_shouldStillRedirectToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("anonymous GET to an unmapped path still redirects to /login")
    void anonymousUnmappedPathRequest_shouldStillRedirectToLogin() throws Exception {
        mockMvc.perform(get("/some/unmapped/path"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("a path that merely resembles /courses/* but has an extra segment is still authenticated-only")
    void pathResemblingCourseMatcher_withExtraSegment_shouldRemainAuthenticatedOnly() throws Exception {
        mockMvc.perform(get("/courses/{courseId}/extra", COURSE_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("POST /courses/{courseId} is not permitted — the matcher is GET-only")
    void postToCourseRoute_shouldRemainAuthenticatedOnly() throws Exception {
        // A mutating verb falls through to anyRequest().authenticated(), so no
        // Authorization decision ever grants it. CSRF protection intercepts an
        // unauthenticated POST with no token even earlier in the chain, so the
        // observable outcome is 403 (not a /login redirect) — either way, the
        // request never reaches CourseDetailController.
        mockMvc.perform(post("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("anonymous GET /courses/{courseId}/lessons/{lessonId} returns 200 with no redirect to /login")
    void anonymousLessonRequest_shouldReturn200_withNoLoginRedirect() throws Exception {
        stubCourseDetail();
        stubGrantedLesson();

        mockMvc.perform(get("/courses/{courseId}/lessons/{lessonId}", COURSE_ID, LESSON_ID))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /courses/{courseId}/lessons/{lessonId} is not permitted — the matcher is GET-only")
    void postToLessonRoute_shouldRemainAuthenticatedOnly() throws Exception {
        // Same reasoning as postToCourseRoute_shouldRemainAuthenticatedOnly: a
        // mutating verb never reaches LessonViewController.
        mockMvc.perform(post("/courses/{courseId}/lessons/{lessonId}", COURSE_ID, LESSON_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a path resembling the lesson route with an extra segment is still authenticated-only")
    void pathResemblingLessonMatcher_withExtraSegment_shouldRemainAuthenticatedOnly() throws Exception {
        mockMvc.perform(get("/courses/{courseId}/lessons/{lessonId}/extra", COURSE_ID, LESSON_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // --- Plans route (#177) ----------------------------------------------
    //
    // Extends this same class rather than a separate one, per this module's
    // established convention (#170): every new anonymous-reachable route
    // gets its both-directions regression coverage added here, not split
    // into a per-feature security test class.

    private static final String PLANS_BODY = """
            {
              "plans": [
                {
                  "id": "plan-1",
                  "name": "Plan Mensual",
                  "description": "Acceso completo por un mes",
                  "price": 4999.00,
                  "currency": "ARS",
                  "durationDays": 30,
                  "featured": false
                }
              ]
            }
            """;

    private void stubPlans() {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo("/api/v1/billing/plans"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(PLANS_BODY)));
    }

    @Test
    @DisplayName("anonymous GET /plans returns 200 with no redirect to /login")
    void anonymousPlansRequest_shouldReturn200_withNoLoginRedirect() throws Exception {
        stubPlans();

        mockMvc.perform(get("/plans"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a path resembling /plans but with an extra segment is still authenticated-only")
    void pathResemblingPlansMatcher_withExtraSegment_shouldRemainAuthenticatedOnly() throws Exception {
        mockMvc.perform(get("/plans/x"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("POST /plans is not permitted — the matcher is GET-only")
    void postToPlansRoute_shouldRemainAuthenticatedOnly() throws Exception {
        // Same reasoning as postToCourseRoute_shouldRemainAuthenticatedOnly: a
        // mutating verb never reaches PlansController.
        mockMvc.perform(post("/plans"))
                .andExpect(status().isForbidden());
    }
}
