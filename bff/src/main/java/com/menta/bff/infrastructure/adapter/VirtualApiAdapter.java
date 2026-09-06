package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.dto.LessonDetail;
import com.menta.bff.application.dto.LessonStream;
import com.menta.bff.application.port.out.VirtualApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
            throw mapHttpException(e);
        } catch (Exception e) {
            log.error("Virtual API course detail call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Virtual API unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public LessonDetail getLesson(String lessonId, String accessToken) {
        throw new UnsupportedOperationException("implemented in PR 1b");
    }

    @Override
    public LessonStream getStream(String lessonId, String accessToken) {
        throw new UnsupportedOperationException("implemented in PR 1b");
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

        throw mapErrorStatus(response, status);
    }

    /**
     * Maps an error {@link ClientResponse} status to a domain exception, never
     * parsing the body for a {@code 403} (forbidden bodies are never trusted).
     */
    private RuntimeException mapErrorStatus(ClientResponse response, HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return new NotFoundException("Course not found: " + status);
        }

        if (status == HttpStatus.FORBIDDEN) {
            return new ForbiddenException("Forbidden: " + status);
        }

        if (status.is5xxServerError()) {
            Long retryAfter = parseRetryAfter(response.headers().asHttpHeaders().getFirst(RETRY_AFTER_HEADER));
            return new ServiceUnavailableException("Virtual API unavailable: " + status, retryAfter);
        }

        String errorBody = response.bodyToMono(String.class).block();
        log.warn("Virtual API course detail returned unexpected status {}: {}", status, errorBody);
        return new RuntimeException("Virtual API call failed with status " + status + ": " + errorBody);
    }

    /**
     * Maps a {@link WebClientResponseException} to a domain exception, never
     * parsing the body for a {@code 403} (forbidden bodies are never trusted).
     */
    private RuntimeException mapHttpException(WebClientResponseException e) {
        HttpStatus status = (HttpStatus) e.getStatusCode();

        if (status == HttpStatus.NOT_FOUND) {
            return new NotFoundException("Course not found: " + status, e);
        }

        if (status == HttpStatus.FORBIDDEN) {
            return new ForbiddenException("Forbidden: " + status, e);
        }

        if (status.is5xxServerError()) {
            Long retryAfter = parseRetryAfter(e.getHeaders().getFirst(RETRY_AFTER_HEADER));
            return new ServiceUnavailableException("Virtual API unavailable: " + status, retryAfter);
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
}
