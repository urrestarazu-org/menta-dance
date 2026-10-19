package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/** Thrown when a lesson id does not exist (US-VIRTUAL-006 management endpoints). */
public class LessonNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "LESSON_NOT_FOUND";

    public LessonNotFoundException() {
        super(ERROR_CODE, "Lesson not found");
    }
}
