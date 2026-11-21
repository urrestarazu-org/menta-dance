package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.CreateVirtualCourseCommand;
import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.dto.VirtualCourseChanges;
import com.menta.bff.application.dto.VirtualCourseDetail;
import java.util.List;

/**
 * Port for the management of virtual courses (#367): the calls of the Virtual API under
 * {@code /api/v1/admin/virtual/courses}. The catalog the students read is {@link VirtualApiClient}'s.
 *
 * <p>The access token only ever travels in the {@code Authorization} header: it is never logged nor put in an
 * exception message, and neither are identifiers, titles or any response body. Every method throws
 * {@link VirtualCourseManagementException} when the API refuses with one of the expected codes, and
 * {@link ServiceUnavailableException} for everything else (timeout, 401, 429, 5xx, an unexpected status, code or
 * body). Only the exact success status counts: 200, 201 when creating and 204 when deleting; any other 2xx is
 * unavailable. Ownership and the lifecycle rules are the API's to decide.
 * </p>
 */
public interface VirtualCourseManagementApiClient {

    /** Lists the courses the caller manages: all of them for an administrator, the own ones for an instructor. */
    List<ManagedVirtualCourse> listManagedCourses(String accessToken);

    /** Reads a course with its modules and lessons. */
    VirtualCourseDetail getManagedCourse(String accessToken, String courseId);

    /** Creates a course, always as a draft. */
    ManagedVirtualCourse createCourse(String accessToken, CreateVirtualCourseCommand command);

    /** Changes a course; the {@code null} fields of {@code changes} stay as they are. */
    ManagedVirtualCourse updateCourse(String accessToken, String courseId, VirtualCourseChanges changes);

    /** Publishes a course; the API refuses with {@code COURSE_NOT_PUBLISHABLE} when it is not ready. */
    ManagedVirtualCourse publishCourse(String accessToken, String courseId);

    /** Takes a course back to draft. */
    ManagedVirtualCourse unpublishCourse(String accessToken, String courseId);

    /** Deletes a draft course. */
    void deleteCourse(String accessToken, String courseId);

    /**
     * The API refused the request with a code the screens word: 400, 403, 404 or 409 with one of the
     * {@link Code}s. Its message is for logs only.
     */
    class VirtualCourseManagementException extends RuntimeException {

        /** The codes of {@code Problem.code} the API answers on these endpoints. */
        public enum Code {
            INVALID_REQUEST,
            /** An instructor sent the id of another instructor. */
            PROFESSOR_MISMATCH,
            /** An instructor does not own the course. */
            COURSE_NOT_OWNED,
            COURSE_NOT_FOUND,
            /** Only a draft course can be deleted. */
            COURSE_NOT_DRAFT,
            /** The course is not ready to publish; see {@link #getPublishDetail()}. */
            COURSE_NOT_PUBLISHABLE
        }

        private final Code code;
        private final String publishDetail;

        public VirtualCourseManagementException(Code code) {
            this(code, null);
        }

        /**
         * Builds a refusal.
         *
         * @param code          what the API refused
         * @param publishDetail the API's explanation, already bounded; only for {@link Code#COURSE_NOT_PUBLISHABLE},
         *                      the one {@code detail} the API words in Spanish for the screens. {@code null}
         *                      otherwise
         */
        public VirtualCourseManagementException(Code code, String publishDetail) {
            super("Virtual API refused the request: " + code);
            this.code = code;
            this.publishDetail = publishDetail;
        }

        public Code getCode() {
            return code;
        }

        /** Why the course cannot be published, in Spanish; {@code null} when there is none to show. */
        public String getPublishDetail() {
            return publishDetail;
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
