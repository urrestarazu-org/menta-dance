package com.menta.bff.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.dto.CourseProgress;
import com.menta.bff.application.dto.LessonDetail;
import com.menta.bff.application.dto.LessonStream;
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
    private static final String LESSON_ID = "lesson-1";
    private static final String LESSON_PATH = "/api/v1/virtual/lessons/" + LESSON_ID;
    private static final String LESSON_STREAM_PATH = LESSON_PATH + "/stream";
    private static final String COURSE_PROGRESS_PATH = "/api/v1/virtual/courses/" + COURSE_ID + "/progress";
    private static final String ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.token";

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

    // -- getLesson ------------------------------------------------------

    @Test
    @DisplayName("should return LessonDetail with a null videoId when the lesson is free")
    void shouldReturnLessonDetailWithNullVideoIdWhenTheLessonIsFree() {
        // Given
        stubFor(get(urlEqualTo(LESSON_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(lessonResponseBody(null)))));

        // When
        LessonDetail result = virtualApiAdapter.getLesson(LESSON_ID, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.lessonId()).isEqualTo(LESSON_ID);
        assertThat(result.videoId()).isNull();
        assertThat(result.course().courseId()).isEqualTo(COURSE_ID);
        assertThat(result.module().moduleId()).isEqualTo("module-1");
        assertThat(result.navigation().nextLesson().lessonId()).isEqualTo("lesson-2");
        assertThat(result.navigation().previousLesson()).isNull();
    }

    @Test
    @DisplayName("should return LessonDetail with a non-null videoId when the lesson is premium accessible")
    void shouldReturnLessonDetailWithVideoIdWhenTheLessonIsPremiumAccessible() {
        // Given
        stubFor(get(urlEqualTo(LESSON_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(lessonResponseBody("bunny-video-id")))));

        // When
        LessonDetail result = virtualApiAdapter.getLesson(LESSON_ID, ACCESS_TOKEN);

        // Then
        assertThat(result.videoId()).isEqualTo("bunny-video-id");
    }

    @Test
    @DisplayName("should throw ForbiddenException on a bare 403 without parsing the body")
    void shouldThrowForbiddenExceptionOnLessonWithoutParsingBody() {
        // Given
        stubFor(get(urlEqualTo(LESSON_PATH))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("not-valid-json-at-all")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getLesson(LESSON_ID, ACCESS_TOKEN))
                .isInstanceOf(VirtualApiClient.ForbiddenException.class);
    }

    @Test
    @DisplayName("should throw NotFoundException when the lesson does not exist")
    void shouldThrowNotFoundExceptionWhenLessonDoesNotExist() {
        // Given
        stubFor(get(urlEqualTo(LESSON_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Lesson not found\"}")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getLesson(LESSON_ID, null))
                .isInstanceOf(VirtualApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException preserving Retry-After when the lesson call fails")
    void shouldThrowServiceUnavailableExceptionWhenLessonCallFails() {
        // Given
        stubFor(get(urlEqualTo(LESSON_PATH))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "45")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getLesson(LESSON_ID, null))
                .isInstanceOf(VirtualApiClient.ServiceUnavailableException.class)
                .satisfies(exception -> assertThat(
                        ((VirtualApiClient.ServiceUnavailableException) exception).getRetryAfterSeconds())
                        .isEqualTo(45L));
    }

    @Test
    @DisplayName("should send Authorization: Bearer <token> on the lesson call when a token is present")
    void shouldSendBearerTokenOnLessonCallWhenTokenPresent() {
        // Given
        stubFor(get(urlEqualTo(LESSON_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(lessonResponseBody(null)))));

        // When
        virtualApiAdapter.getLesson(LESSON_ID, ACCESS_TOKEN);

        // Then
        verify(getRequestedFor(urlEqualTo(LESSON_PATH))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    @DisplayName("should omit the Authorization header entirely on the lesson call when the token is null")
    void shouldOmitAuthorizationHeaderOnLessonCallWhenTokenIsNull() {
        // Given
        stubFor(get(urlEqualTo(LESSON_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(lessonResponseBody(null)))));

        // When
        virtualApiAdapter.getLesson(LESSON_ID, null);

        // Then
        verify(getRequestedFor(urlEqualTo(LESSON_PATH))
                .withoutHeader("Authorization"));
    }

    // -- getStream --------------------------------------------------------

    @Test
    @DisplayName("should return LessonStream when the upstream grants access with a token")
    void shouldReturnLessonStreamWhenUpstreamGrantsAccessWithToken() {
        // Given
        stubFor(get(urlEqualTo(LESSON_STREAM_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(streamResponseBody()))));

        // When
        LessonStream result = virtualApiAdapter.getStream(LESSON_ID, ACCESS_TOKEN);

        // Then
        assertThat(result.url()).isEqualTo("https://cdn.menta.dance/signed.m3u8");
        assertThat(result.expiresAt()).isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("should return LessonStream for a free lesson requested anonymously (null token)")
    void shouldReturnLessonStreamForFreeLessonWithNullToken() {
        // A granted lesson always needs the stream call, free ones included:
        // a 200 here must not be skipped just because the caller is anonymous.
        // Given
        stubFor(get(urlEqualTo(LESSON_STREAM_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(streamResponseBody()))));

        // When
        LessonStream result = virtualApiAdapter.getStream(LESSON_ID, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.url()).isEqualTo("https://cdn.menta.dance/signed.m3u8");

        verify(getRequestedFor(urlEqualTo(LESSON_STREAM_PATH))
                .withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("should throw ForbiddenException on a bare 403 from the stream call without parsing the body")
    void shouldThrowForbiddenExceptionOnStreamWithoutParsingBody() {
        // Given
        stubFor(get(urlEqualTo(LESSON_STREAM_PATH))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("not-valid-json-at-all")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getStream(LESSON_ID, ACCESS_TOKEN))
                .isInstanceOf(VirtualApiClient.ForbiddenException.class);
    }

    @Test
    @DisplayName("should throw NotFoundException when the stream's lesson does not exist")
    void shouldThrowNotFoundExceptionWhenStreamLessonDoesNotExist() {
        // Given
        stubFor(get(urlEqualTo(LESSON_STREAM_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Lesson not found\"}")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getStream(LESSON_ID, null))
                .isInstanceOf(VirtualApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException preserving Retry-After when the stream call fails")
    void shouldThrowServiceUnavailableExceptionWhenStreamCallFails() {
        // Given
        stubFor(get(urlEqualTo(LESSON_STREAM_PATH))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "12")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getStream(LESSON_ID, ACCESS_TOKEN))
                .isInstanceOf(VirtualApiClient.ServiceUnavailableException.class)
                .satisfies(exception -> assertThat(
                        ((VirtualApiClient.ServiceUnavailableException) exception).getRetryAfterSeconds())
                        .isEqualTo(12L));
    }

    @Test
    @DisplayName("should send Authorization: Bearer <token> on the stream call when a token is present")
    void shouldSendBearerTokenOnStreamCallWhenTokenPresent() {
        // Given
        stubFor(get(urlEqualTo(LESSON_STREAM_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(streamResponseBody()))));

        // When
        virtualApiAdapter.getStream(LESSON_ID, ACCESS_TOKEN);

        // Then
        verify(getRequestedFor(urlEqualTo(LESSON_STREAM_PATH))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    // -- getCourseProgress ------------------------------------------------

    @Test
    @DisplayName("should return CourseProgress with a resolved resumeLesson when upstream responds with 200")
    void shouldReturnCourseProgressWithResumeLessonWhenUpstreamRespondsWith200() {
        // Given
        stubFor(get(urlEqualTo(COURSE_PROGRESS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(courseProgressResponseBody(true)))));

        // When
        CourseProgress result = virtualApiAdapter.getCourseProgress(COURSE_ID, ACCESS_TOKEN);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.courseId()).isEqualTo(COURSE_ID);
        assertThat(result.completedLessons()).isEqualTo(3);
        assertThat(result.totalLessons()).isEqualTo(10);
        assertThat(result.percentage()).isEqualTo(30);
        assertThat(result.resumeLesson()).isNotNull();
        assertThat(result.resumeLesson().lessonId()).isEqualTo(LESSON_ID);
        assertThat(result.resumeLesson().moduleId()).isEqualTo("module-1");
        assertThat(result.resumeLesson().positionSeconds()).isEqualTo(42);
        assertThat(result.resumeLesson().completed()).isFalse();
    }

    @Test
    @DisplayName("should return CourseProgress with a null resumeLesson when upstream reports zero progress")
    void shouldReturnCourseProgressWithNullResumeLessonWhenUpstreamReportsZeroProgress() {
        // Given
        stubFor(get(urlEqualTo(COURSE_PROGRESS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(courseProgressResponseBody(false)))));

        // When
        CourseProgress result = virtualApiAdapter.getCourseProgress(COURSE_ID, ACCESS_TOKEN);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.resumeLesson()).isNull();
    }

    @Test
    @DisplayName("should throw NotFoundException when the course progress endpoint responds with 404")
    void shouldThrowNotFoundExceptionWhenCourseProgressDoesNotExist() {
        // Given
        stubFor(get(urlEqualTo(COURSE_PROGRESS_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Course not found\"}")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getCourseProgress(COURSE_ID, ACCESS_TOKEN))
                .isInstanceOf(VirtualApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("should throw ForbiddenException on a bare 403 from the course progress call without parsing the body")
    void shouldThrowForbiddenExceptionOnCourseProgressWithoutParsingBody() {
        // Given
        stubFor(get(urlEqualTo(COURSE_PROGRESS_PATH))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("not-valid-json-at-all")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getCourseProgress(COURSE_ID, ACCESS_TOKEN))
                .isInstanceOf(VirtualApiClient.ForbiddenException.class);
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException preserving Retry-After when the course progress call fails")
    void shouldThrowServiceUnavailableExceptionWhenCourseProgressCallFails() {
        // Given
        stubFor(get(urlEqualTo(COURSE_PROGRESS_PATH))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "20")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getCourseProgress(COURSE_ID, ACCESS_TOKEN))
                .isInstanceOf(VirtualApiClient.ServiceUnavailableException.class)
                .satisfies(exception -> assertThat(
                        ((VirtualApiClient.ServiceUnavailableException) exception).getRetryAfterSeconds())
                        .isEqualTo(20L));
    }

    @Test
    @DisplayName("should fall through to a generic RuntimeException on an unexpected 401 from the course progress call")
    void shouldFallThroughToGenericRuntimeExceptionOnCourseProgressWith401() {
        // 401 is unexpected (this call is only ever made with a token) and is
        // deliberately not given its own branch in the shared mappers (design D3) —
        // this test proves the gap is intentional, not an oversight.
        // Given
        stubFor(get(urlEqualTo(COURSE_PROGRESS_PATH))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Unauthorized\"}")));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getCourseProgress(COURSE_ID, ACCESS_TOKEN))
                .isExactlyInstanceOf(RuntimeException.class)
                .isNotInstanceOf(VirtualApiClient.NotFoundException.class)
                .isNotInstanceOf(VirtualApiClient.ForbiddenException.class)
                .isNotInstanceOf(VirtualApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("should always send Authorization: Bearer <token> on the course progress call")
    void shouldAlwaysSendBearerTokenOnCourseProgressCall() {
        // Given
        stubFor(get(urlEqualTo(COURSE_PROGRESS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(courseProgressResponseBody(false)))));

        // When
        virtualApiAdapter.getCourseProgress(COURSE_ID, ACCESS_TOKEN);

        // Then
        verify(getRequestedFor(urlEqualTo(COURSE_PROGRESS_PATH))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)));
    }

    @Test
    @DisplayName("should throw NullPointerException and make no request when accessToken is null")
    void shouldThrowNullPointerExceptionAndMakeNoRequestWhenAccessTokenIsNull() {
        // Given
        stubFor(get(urlEqualTo(COURSE_PROGRESS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(courseProgressResponseBody(false)))));

        // When / Then
        assertThatThrownBy(() -> virtualApiAdapter.getCourseProgress(COURSE_ID, null))
                .isInstanceOf(NullPointerException.class);

        // The guard must fire before any WireMock request is recorded — unlike
        // getLesson/getStream's conditional header, there is no anonymous form
        // for this call at all (design D3).
        verify(0, getRequestedFor(urlEqualTo(COURSE_PROGRESS_PATH)));
    }

    private Map<String, Object> courseProgressResponseBody(boolean withResumeLesson) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("courseId", COURSE_ID);
        body.put("completedLessons", 3);
        body.put("totalLessons", 10);
        body.put("percentage", 30);
        body.put("resumeLesson", withResumeLesson
                ? Map.of(
                        "lessonId", LESSON_ID,
                        "moduleId", "module-1",
                        "positionSeconds", 42,
                        "completed", false)
                : null);
        return body;
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

    private Map<String, Object> lessonResponseBody(String videoId) {
        Map<String, Object> lesson = new java.util.HashMap<>();
        lesson.put("lessonId", LESSON_ID);
        lesson.put("title", "Lesson 1");
        lesson.put("description", "First lesson");
        lesson.put("duration", "05:00");
        lesson.put("order", 1);
        lesson.put("videoId", videoId);
        lesson.put("course", Map.of("courseId", COURSE_ID, "title", "Intro to Salsa"));
        lesson.put("module", Map.of("moduleId", "module-1", "title", "Module 1"));

        Map<String, Object> navigation = new java.util.HashMap<>();
        navigation.put("previousLesson", null);
        navigation.put("nextLesson", Map.of("lessonId", "lesson-2", "title", "Lesson 2", "isFree", false));

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("lesson", lesson);
        body.put("navigation", navigation);
        body.put("subscription", Map.of("message", "Suscríbete para ver esta lección", "plansUrl", "/plans"));
        body.put("access", Map.of("preview", videoId == null, "requiresSubscription", false));
        return body;
    }

    private Map<String, Object> streamResponseBody() {
        return Map.of(
                "stream", Map.of(
                        "url", "https://cdn.menta.dance/signed.m3u8",
                        "type", "HLS",
                        "qualities", List.of(),
                        "expiresAt", "2026-01-01T00:00:00Z"
                ),
                "lesson", Map.of(
                        "lessonId", LESSON_ID,
                        "title", "Lesson 1",
                        "duration", "05:00"
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
