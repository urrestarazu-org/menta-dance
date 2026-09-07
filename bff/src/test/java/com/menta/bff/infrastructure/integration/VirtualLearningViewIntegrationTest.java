package com.menta.bff.infrastructure.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
