package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.VideoLesson;
import com.menta.bff.application.dto.VideoListQuery;
import com.menta.bff.application.dto.VideoPage;
import java.util.List;

/**
 * Port for the management of the videos of the library (#842, ADR-0049): the calls of the Virtual API under
 * {@code /api/v1/admin/virtual/videos}. The BFF never talks to Bunny: the API owns the credential and the library.
 *
 * <p>The access token only ever travels in the {@code Authorization} header: it is never logged nor put in an
 * exception message, and neither are identifiers, titles, search texts or any response body. Every method throws
 * {@link VideoManagementException} when the API refuses with one of the expected codes, and
 * {@link ServiceUnavailableException} for everything else (timeout, 401, 429, 5xx, an unexpected status, code or
 * body). Only the exact success status counts: 200 to list and rename, 204 to delete. Which videos the caller
 * sees or may change is the API's to decide.
 * </p>
 */
public interface VirtualVideoManagementApiClient {

    /**
     * Lists the videos the caller sees: the whole library and the unknown ones for an administrator, the ones of
     * the own courses for an instructor.
     *
     * @param accessToken caller's access token
     * @param query       normalized search and paging
     * @return the requested page; past the last page it has no items but carries the totals
     */
    VideoPage listVideos(String accessToken, VideoListQuery query);

    /**
     * Renames a video. The body of the answer is not read: the rename is done once the API says {@code 200}.
     *
     * @param accessToken caller's access token
     * @param videoId     identifier of the video, already checked by the caller
     * @param title       new title, already trimmed and checked by the caller
     */
    void renameVideo(String accessToken, String videoId, String title);

    /**
     * Deletes a video that no lesson uses; the API answers {@code 204} also for one that is already gone.
     *
     * @param accessToken caller's access token
     * @param videoId     identifier of the video, already checked by the caller
     */
    void deleteVideo(String accessToken, String videoId);

    /**
     * The API refused the request with a code the screens word: 400, 403, 404, 409 or 503 with one of the
     * {@link Code}s. Its message is for logs only.
     */
    class VideoManagementException extends RuntimeException {

        /** The codes of {@code Problem.code} the API answers on these endpoints. */
        public enum Code {
            INVALID_REQUEST,
            /** The role cannot manage videos at all. */
            ACCESS_DENIED,
            /** An instructor does not use the video in any lesson of the own courses. */
            VIDEO_NOT_OWNED,
            VIDEO_NOT_FOUND,
            /** A lesson uses the video, so it cannot be deleted; see {@link #getLessons()}. */
            VIDEO_IN_USE,
            /** The library did not answer. */
            VIDEO_LIBRARY_UNAVAILABLE
        }

        private final Code code;
        private final List<VideoLesson> lessons;

        public VideoManagementException(Code code) {
            this(code, List.of());
        }

        /**
         * Builds a refusal.
         *
         * @param code    what the API refused
         * @param lessons the lessons that use the video; only for {@link Code#VIDEO_IN_USE}, empty otherwise
         */
        public VideoManagementException(Code code, List<VideoLesson> lessons) {
            super("Virtual API refused the request: " + code);
            this.code = code;
            this.lessons = List.copyOf(lessons);
        }

        public Code getCode() {
            return code;
        }

        /** The lessons that use the video, in the API's order; empty unless the code is {@code VIDEO_IN_USE}. */
        public List<VideoLesson> getLessons() {
            return lessons;
        }
    }

    /** The management data could not be obtained or changed. Its message is for logs only. */
    class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
