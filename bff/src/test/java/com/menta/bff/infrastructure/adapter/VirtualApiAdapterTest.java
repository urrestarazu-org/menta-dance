package com.menta.bff.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.port.out.VirtualApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
@DisplayName("VirtualApiAdapter")
class VirtualApiAdapterTest {

    private static final String COURSE_ID = "course-1";
    private static final String COURSE_DETAIL_PATH = "/api/v1/catalog/courses/" + COURSE_ID;

    private VirtualApiAdapter virtualApiAdapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        String baseUrl = wmRuntimeInfo.getHttpBaseUrl();

        VirtualApiProperties virtualApiProperties = new VirtualApiProperties();
        virtualApiProperties.setBaseUrl(baseUrl);
        virtualApiProperties.setTimeout(Duration.ofSeconds(5));

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

        objectMapper = new ObjectMapper();
        virtualApiAdapter = new VirtualApiAdapter(webClient, virtualApiProperties);
    }

    @Test
    @DisplayName("should return course detail when upstream responds with 200")
    void shouldReturnCourseDetailWhenUpstreamRespondsWith200() {
        // Given
        stubFor(get(urlEqualTo(COURSE_DETAIL_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(courseDetailResponseBody()))));

        // When
        CourseDetail result = virtualApiAdapter.getCourseDetail(COURSE_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.courseId()).isEqualTo(COURSE_ID);
        assertThat(result.title()).isEqualTo("Intro to Salsa");
        assertThat(result.isPremium()).isFalse();
        assertThat(result.modules()).hasSize(1);
        assertThat(result.modules().get(0).lessons()).hasSize(1);
        assertThat(result.modules().get(0).lessons().get(0).lessonId()).isEqualTo("lesson-1");
        assertThat(result.stats().moduleCount()).isEqualTo(1);
        assertThat(result.stats().totalDuration()).isEqualTo("5m");
    }

    @Test
    @DisplayName("should throw NotFoundException when upstream responds with 404")
    void shouldThrowNotFoundExceptionWhenUpstreamRespondsWith404() {
        // Given
        stubFor(get(urlEqualTo(COURSE_DETAIL_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Course not found\"}")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getCourseDetail(COURSE_ID))
                .isInstanceOf(VirtualApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException preserving Retry-After when upstream "
            + "responds with 503")
    void shouldThrowServiceUnavailableExceptionPreservingRetryAfterWhenUpstreamRespondsWith503() {
        // Given
        stubFor(get(urlEqualTo(COURSE_DETAIL_PATH))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/problem+json")
                        .withHeader("Retry-After", "30")
                        .withBody("{\"detail\":\"Service temporarily unavailable\"}")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getCourseDetail(COURSE_ID))
                .isInstanceOf(VirtualApiClient.ServiceUnavailableException.class)
                .satisfies(exception -> assertThat(
                        ((VirtualApiClient.ServiceUnavailableException) exception).getRetryAfterSeconds())
                        .isEqualTo(30L));
    }

    @Test
    @DisplayName("should never send an Authorization header on the catalog call")
    void shouldNeverSendAuthorizationHeaderOnTheCatalogCall() {
        // Given
        stubFor(get(urlEqualTo(COURSE_DETAIL_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(courseDetailResponseBody()))));

        // When
        // D2: getCourseDetail takes no token parameter at all — this is structural,
        // not conditional, proven by the fact the outbound request never carries it.
        virtualApiAdapter.getCourseDetail(COURSE_ID);

        // Then
        verify(getRequestedFor(urlEqualTo(COURSE_DETAIL_PATH))
                .withoutHeader("Authorization"));
    }

    private Map<String, Object> courseDetailResponseBody() {
        return Map.of(
                "courseId", COURSE_ID,
                "title", "Intro to Salsa",
                "description", "Learn the basics",
                "thumbnailUrl", "https://cdn.menta.dance/thumb.jpg",
                "category", "SALSA",
                "level", "BEGINNER",
                "isPremium", false,
                "modules", List.of(Map.of(
                        "moduleId", "module-1",
                        "title", "Module 1",
                        "order", 1,
                        "lessons", List.of(Map.of(
                                "lessonId", "lesson-1",
                                "title", "Lesson 1",
                                "duration", "05:00",
                                "isFree", true,
                                "order", 1
                        ))
                )),
                "stats", Map.of(
                        "moduleCount", 1,
                        "lessonCount", 1,
                        "totalDuration", "5m"
                )
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
