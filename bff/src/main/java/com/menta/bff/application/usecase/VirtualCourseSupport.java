package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;

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

    /** What the adapter cannot send (it would fail on a {@code null}) is the invalid request the API would answer. */
    static <T> T required(T value) {
        if (value == null) {
            throw new VirtualCourseManagementException(Code.INVALID_REQUEST);
        }
        return value;
    }
}
