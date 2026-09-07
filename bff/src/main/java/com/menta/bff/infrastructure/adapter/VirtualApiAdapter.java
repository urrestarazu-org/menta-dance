package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.dto.CourseProgress;
import com.menta.bff.application.dto.LessonDetail;
import com.menta.bff.application.dto.LessonStream;
import com.menta.bff.application.dto.Nav;
import com.menta.bff.application.port.out.VirtualApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Objects;

/**
 * WebClient adapter for the upstream catalog and virtual lesson endpoints.
 * <p>
 * Implements {@link VirtualApiClient} port using Spring WebClient to call
 * api:app's public catalog and api:virtual's lesson endpoints.
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer. Uses an explicit constructor
 * (design decision C) because {@code @Qualifier("virtualApiWebClient")} cannot be
 * carried by Lombok's {@code @RequiredArgsConstructor}.
 * </p>
 */
@Slf4j
@Component
public class VirtualApiAdapter implements VirtualApiClient {

    private static final String COURSE_DETAIL_ENDPOINT = "/api/v1/catalog/courses/{courseId}";
    private static final String LESSON_ENDPOINT = "/api/v1/virtual/lessons/{lessonId}";
    private static final String LESSON_STREAM_ENDPOINT = "/api/v1/virtual/lessons/{lessonId}/stream";
    private static final String COURSE_PROGRESS_ENDPOINT = "/api/v1/virtual/courses/{courseId}/progress";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final WebClient webClient;
    private final VirtualApiProperties virtualApiProperties;

    public VirtualApiAdapter(
            @Qualifier("virtualApiWebClient") WebClient webClient,
            VirtualApiProperties virtualApiProperties) {
        this.webClient = webClient;
        this.virtualApiProperties = virtualApiProperties;
    }

    @Override
    public CourseDetail getCourseDetail(String courseId) {
        Objects.requireNonNull(courseId, "courseId cannot be null");

        log.debug("Calling Virtual API course detail endpoint");

        try {
            ClientResponse response = webClient.get()
                    .uri(COURSE_DETAIL_ENDPOINT, courseId)
                    .exchange()
                    .timeout(virtualApiProperties.getTimeout())
                    .block();

            return handleCourseDetailResponse(response);

        } catch (NotFoundException | ForbiddenException | ServiceUnavailableException e) {
            // Re-throw domain exceptions without wrapping
            throw e;
        } catch (WebClientResponseException e) {
            throw mapHttpException(e, "Course");
        } catch (Exception e) {
            log.error("Virtual API course detail call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Virtual API unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public LessonDetail getLesson(String lessonId, String accessToken) {
        Objects.requireNonNull(lessonId, "lessonId cannot be null");

        log.debug("Calling Virtual API lesson detail endpoint");

        try {
            ClientResponse response = webClient.get()
                    .uri(LESSON_ENDPOINT, lessonId)
                    .headers(headers -> applyBearerToken(headers, accessToken))
                    .exchange()
                    .timeout(virtualApiProperties.getTimeout())
                    .block();

            return handleLessonResponse(response);

        } catch (NotFoundException | ForbiddenException | ServiceUnavailableException e) {
            // Re-throw domain exceptions without wrapping
            throw e;
        } catch (WebClientResponseException e) {
            throw mapHttpException(e, "Lesson");
        } catch (Exception e) {
            log.error("Virtual API lesson detail call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Virtual API unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public LessonStream getStream(String lessonId, String accessToken) {
        Objects.requireNonNull(lessonId, "lessonId cannot be null");

        log.debug("Calling Virtual API lesson stream endpoint");

        try {
            ClientResponse response = webClient.get()
                    .uri(LESSON_STREAM_ENDPOINT, lessonId)
                    .headers(headers -> applyBearerToken(headers, accessToken))
                    .exchange()
                    .timeout(virtualApiProperties.getTimeout())
                    .block();

            return handleStreamResponse(response);

        } catch (NotFoundException | ForbiddenException | ServiceUnavailableException e) {
            // Re-throw domain exceptions without wrapping
            throw e;
        } catch (WebClientResponseException e) {
            throw mapHttpException(e, "Lesson stream");
        } catch (Exception e) {
            log.error("Virtual API lesson stream call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Virtual API unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public CourseProgress getCourseProgress(String courseId, String accessToken) {
        Objects.requireNonNull(courseId, "courseId cannot be null");
        Objects.requireNonNull(accessToken, "accessToken cannot be null");

        log.debug("Calling Virtual API course progress endpoint");

        try {
            ClientResponse response = webClient.get()
                    .uri(COURSE_PROGRESS_ENDPOINT, courseId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .exchange()
                    .timeout(virtualApiProperties.getTimeout())
                    .block();

            return handleCourseProgressResponse(response);

        } catch (WebClientResponseException e) {
            throw mapHttpException(e, "Course progress");
        } catch (RuntimeException e) {
            // Re-throw domain exceptions (NotFoundException/ForbiddenException/
            // ServiceUnavailableException) and the deliberate unmapped-status
            // RuntimeException from mapErrorStatus (e.g. 401) without wrapping —
            // no new exception type is introduced for this call (design D3).
            throw e;
        } catch (Exception e) {
            log.error("Virtual API course progress call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Virtual API unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Sets {@code Authorization: Bearer <token>} only when a token is present.
     * When {@code accessToken} is {@code null} (anonymous caller) the header is
     * omitted entirely — never sent blank (design D2).
     */
    private static void applyBearerToken(HttpHeaders headers, String accessToken) {
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
    }

    /**
     * Handles the course detail response, mapping success/error statuses.
     */
    private CourseDetail handleCourseDetailResponse(ClientResponse response) {
        if (response == null) {
            throw new ServiceUnavailableException("Virtual API returned null response");
        }

        HttpStatus status = (HttpStatus) response.statusCode();

        if (status.is2xxSuccessful()) {
            CourseDetail body = response.bodyToMono(CourseDetail.class).block();
            if (body == null) {
                throw new ServiceUnavailableException("Virtual API returned empty response body");
            }
            return body;
        }

        throw mapErrorStatus(response, status, "Course");
    }

    /**
     * Handles the lesson detail response. The upstream JSON nests the lesson
     * fields and its navigation block as siblings ({@code {lesson: {...},
     * navigation: {...}}}); {@link LessonWireResponse} binds that shape and
     * this method flattens it into {@link LessonDetail} (design 1b.1).
     */
    private LessonDetail handleLessonResponse(ClientResponse response) {
        if (response == null) {
            throw new ServiceUnavailableException("Virtual API returned null response");
        }

        HttpStatus status = (HttpStatus) response.statusCode();

        if (status.is2xxSuccessful()) {
            LessonWireResponse wire = response.bodyToMono(LessonWireResponse.class).block();
            if (wire == null || wire.lesson() == null) {
                throw new ServiceUnavailableException("Virtual API returned empty response body");
            }
            LessonWireResponse.LessonBlock lesson = wire.lesson();
            return new LessonDetail(
                    lesson.lessonId(), lesson.title(), lesson.description(), lesson.duration(),
                    lesson.order(), lesson.videoId(), lesson.course(), lesson.module(),
                    wire.navigation()
            );
        }

        throw mapErrorStatus(response, status, "Lesson");
    }

    /**
     * Handles the lesson stream response, trimming the upstream {@code
     * stream}/{@code lesson} envelope down to {@link LessonStream}'s {@code
     * url}/{@code expiresAt} (design 1b.3 — the player needs nothing else).
     */
    private LessonStream handleStreamResponse(ClientResponse response) {
        if (response == null) {
            throw new ServiceUnavailableException("Virtual API returned null response");
        }

        HttpStatus status = (HttpStatus) response.statusCode();

        if (status.is2xxSuccessful()) {
            StreamWireResponse wire = response.bodyToMono(StreamWireResponse.class).block();
            if (wire == null || wire.stream() == null) {
                throw new ServiceUnavailableException("Virtual API returned empty response body");
            }
            return new LessonStream(wire.stream().url(), wire.stream().expiresAt());
        }

        throw mapErrorStatus(response, status, "Lesson stream");
    }

    /**
     * Handles the course progress response, mapping success/error statuses.
     */
    private CourseProgress handleCourseProgressResponse(ClientResponse response) {
        if (response == null) {
            throw new ServiceUnavailableException("Virtual API returned null response");
        }

        HttpStatus status = (HttpStatus) response.statusCode();

        if (status.is2xxSuccessful()) {
            CourseProgress body = response.bodyToMono(CourseProgress.class).block();
            if (body == null) {
                throw new ServiceUnavailableException("Virtual API returned empty response body");
            }
            return body;
        }

        throw mapErrorStatus(response, status, "Course progress");
    }

    /**
     * Maps an error {@link ClientResponse} status to a domain exception, never
     * parsing the body for a {@code 403} (forbidden bodies are never trusted).
     *
     * @param resourceLabel human-readable resource name for the exception message
     *                      (e.g. {@code "Course"}, {@code "Lesson"})
     */
    private RuntimeException mapErrorStatus(ClientResponse response, HttpStatus status, String resourceLabel) {
        if (status == HttpStatus.NOT_FOUND) {
            return new NotFoundException(resourceLabel + " not found: " + status);
        }

        if (status == HttpStatus.FORBIDDEN) {
            return new ForbiddenException("Forbidden: " + status);
        }

        if (status.is5xxServerError()) {
            Long retryAfter = parseRetryAfter(response.headers().asHttpHeaders().getFirst(RETRY_AFTER_HEADER));
            return new ServiceUnavailableException(resourceLabel + " unavailable: " + status, retryAfter);
        }

        String errorBody = response.bodyToMono(String.class).block();
        log.warn("Virtual API {} call returned unexpected status {}: {}", resourceLabel, status, errorBody);
        return new RuntimeException("Virtual API call failed with status " + status + ": " + errorBody);
    }

    /**
     * Maps a {@link WebClientResponseException} to a domain exception, never
     * parsing the body for a {@code 403} (forbidden bodies are never trusted).
     *
     * @param resourceLabel human-readable resource name for the exception message
     */
    private RuntimeException mapHttpException(WebClientResponseException e, String resourceLabel) {
        HttpStatus status = (HttpStatus) e.getStatusCode();

        if (status == HttpStatus.NOT_FOUND) {
            return new NotFoundException(resourceLabel + " not found: " + status, e);
        }

        if (status == HttpStatus.FORBIDDEN) {
            return new ForbiddenException("Forbidden: " + status, e);
        }

        if (status.is5xxServerError()) {
            Long retryAfter = parseRetryAfter(e.getHeaders().getFirst(RETRY_AFTER_HEADER));
            return new ServiceUnavailableException(resourceLabel + " unavailable: " + status, retryAfter);
        }

        return new RuntimeException("Virtual API call failed: " + status, e);
    }

    private Long parseRetryAfter(String header) {
        if (header == null) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Wire shape for {@code GET /api/v1/virtual/lessons/{lessonId}}, mirroring
     * {@code PublicLessonFreeResponse}/{@code PublicLessonPremiumAccessibleResponse}.
     * Kept private to this adapter — the flattened {@link LessonDetail} is what
     * {@code application} sees, so no Jackson-shaped record leaks past infrastructure.
     */
    private record LessonWireResponse(LessonBlock lesson, Nav navigation) {

        private record LessonBlock(
                String lessonId,
                String title,
                String description,
                String duration,
                int order,
                String videoId,
                LessonDetail.CourseRef course,
                LessonDetail.ModuleRef module
        ) {
        }
    }

    /**
     * Wire shape for {@code GET /api/v1/virtual/lessons/{lessonId}/stream},
     * mirroring {@code PublicLessonStreamResponse}. Only {@code stream.url}/
     * {@code stream.expiresAt} are extracted into {@link LessonStream}; the
     * sibling {@code lesson} block and the fixed {@code type}/{@code qualities}
     * fields are intentionally left unbound.
     */
    private record StreamWireResponse(StreamBlock stream) {

        private record StreamBlock(String url, Instant expiresAt) {
        }
    }
}
