package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** What the virtual course management use cases share: the checks of what can never be sent to the API. */
final class VirtualCourseSupport {

    private VirtualCourseSupport() {
    }

    /** A call without an access token cannot succeed: it reads as unavailable, without calling the API. */
    static String token(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ServiceUnavailableException("No access token for the Virtual API");
        }
        return accessToken;
    }

    /** Only a canonical UUID reaches a path; anything else cannot be a course. */
    static String courseId(String courseId) {
        String id = UuidFormat.normalize(courseId);
        if (id == null) {
            throw new VirtualCourseManagementException(Code.COURSE_NOT_FOUND);
        }
        return id;
    }

    /** Only a canonical UUID reaches a path; anything else cannot be a module. */
    static String moduleId(String moduleId) {
        String id = UuidFormat.normalize(moduleId);
        if (id == null) {
            throw new VirtualCourseManagementException(Code.MODULE_NOT_FOUND);
        }
        return id;
    }

    /** Only a canonical UUID reaches a path; anything else cannot be a lesson. */
    static String lessonId(String lessonId) {
        String id = UuidFormat.normalize(lessonId);
        if (id == null) {
            throw new VirtualCourseManagementException(Code.LESSON_NOT_FOUND);
        }
        return id;
    }

    /**
     * The ids of a reorder, each normalized. A missing or empty list, or an id that is not a UUID, is an invalid
     * request; a repeated id can never be the exact set of modules of a course.
     */
    static List<String> moduleIds(List<String> moduleIds) {
        if (required(moduleIds).isEmpty()) {
            throw new VirtualCourseManagementException(Code.INVALID_REQUEST);
        }
        List<String> ids = moduleIds.stream().map(id -> required(UuidFormat.normalize(id))).toList();
        Set<String> distinct = new HashSet<>(ids);
        if (distinct.size() != ids.size()) {
            throw new VirtualCourseManagementException(Code.MODULE_REORDER_MISMATCH);
        }
        return ids;
    }

    /** What the adapter cannot send (it would fail on a {@code null}) is the invalid request the API would answer. */
    static <T> T required(T value) {
        if (value == null) {
            throw new VirtualCourseManagementException(Code.INVALID_REQUEST);
        }
        return value;
    }
}
