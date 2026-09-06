package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.dto.LessonDetail;
import com.menta.bff.application.dto.LessonStream;

/**
 * Port for communicating with the upstream catalog and virtual lesson endpoints:
 * course detail (public, never authenticated), lesson detail and lesson stream
 * (both conditionally authenticated, implemented in PR 1b). Part of Clean
 * Architecture application layer — abstracts HTTP/REST details.
 */
public interface VirtualApiClient {

    /**
     * Retrieves the public course detail projection.
     * <p>
     * Calls GET /api/v1/catalog/courses/{courseId}. Never sends an
     * {@code Authorization} header — catalog access is caller-agnostic (design D2).
     * </p>
     *
     * @param courseId course identifier
     * @return course detail (modules, lessons, stats)
     * @throws NotFoundException           if the course does not exist or is not published (404)
     * @throws ServiceUnavailableException if the upstream is unreachable (503)
     */
    CourseDetail getCourseDetail(String courseId);

    /**
     * Retrieves lesson detail, gated by subscription entitlement.
     * <p>
     * Calls GET /api/v1/virtual/lessons/{lessonId} with a conditional Bearer token:
     * present iff {@code accessToken} is non-null, omitted entirely (not blank)
     * when {@code null} (design D2).
     * </p>
     *
     * @param lessonId    lesson identifier
     * @param accessToken caller's access token, or {@code null} for an anonymous caller
     * @return lesson detail
     * @throws NotFoundException           if the lesson does not exist (404)
     * @throws ForbiddenException          if the caller lacks entitlement (403)
     * @throws ServiceUnavailableException if the upstream is unreachable (503)
     */
    LessonDetail getLesson(String lessonId, String accessToken);

    /**
     * Retrieves a signed lesson stream URL, gated by subscription entitlement.
     * <p>
     * Calls GET /api/v1/virtual/lessons/{lessonId}/stream with the same
     * conditional Bearer token rule as {@link #getLesson}. A granted lesson
     * always needs this call, free ones included — {@code videoId == null}
     * on the lesson detail does not mean no stream exists.
     * </p>
     *
     * @param lessonId    lesson identifier
     * @param accessToken caller's access token, or {@code null} for an anonymous caller
     * @return lesson stream descriptor
     * @throws NotFoundException           if the lesson does not exist (404)
     * @throws ForbiddenException          if the caller lacks entitlement (403)
     * @throws ServiceUnavailableException if the upstream is unreachable (503)
     */
    LessonStream getStream(String lessonId, String accessToken);

    /**
     * Exception thrown when the requested resource does not exist upstream (404).
     */
    class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }

        public NotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the upstream denies access (bare 403, body never parsed).
     */
    class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }

        public ForbiddenException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the upstream is unavailable, optionally carrying the
     * {@code Retry-After} value in seconds when the upstream provided one.
     */
    class ServiceUnavailableException extends RuntimeException {

        private final Long retryAfterSeconds;

        public ServiceUnavailableException(String message) {
            super(message);
            this.retryAfterSeconds = null;
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
            this.retryAfterSeconds = null;
        }

        public ServiceUnavailableException(String message, Long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public Long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
