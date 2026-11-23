package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when deleting a course whose students already have lesson progress (#834). A course can go back
 * to {@code DRAFT} after it was published, so the draft-only rule does not rule out student history; the
 * delete is rejected instead of erasing it.
 */
public class CourseHasProgressException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_HAS_PROGRESS";

    public CourseHasProgressException() {
        super(ERROR_CODE, "A course with student progress cannot be deleted");
    }
}
