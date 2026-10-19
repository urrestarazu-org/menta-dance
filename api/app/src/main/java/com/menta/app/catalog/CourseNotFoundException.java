package com.menta.app.catalog;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a {@code courseId} does not resolve in either Physical or
 * Virtual (#95 acceptance criteria). Deliberately does not distinguish
 * "never existed" from "exists in one module but is not published/active" —
 * same anti-enumeration discipline both modules already apply to their own
 * not-found paths.
 */
public class CourseNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_NOT_FOUND";

    public CourseNotFoundException() {
        super(ERROR_CODE, "Course not found in any modality");
    }
}
