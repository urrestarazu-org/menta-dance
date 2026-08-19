package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/** Thrown when a course id does not exist (US-VIRTUAL-006 management endpoints). */
public class CourseNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_NOT_FOUND";

    public CourseNotFoundException() {
        super(ERROR_CODE, "Course not found");
    }
}
