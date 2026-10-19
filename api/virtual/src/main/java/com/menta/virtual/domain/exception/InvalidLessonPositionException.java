package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/** Thrown when a saved position is negative or exceeds the lesson's duration bound. */
public class InvalidLessonPositionException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_LESSON_POSITION";

    public InvalidLessonPositionException(int positionSeconds, int maxSeconds) {
        super(ERROR_CODE, "Position " + positionSeconds + " is out of bounds [0, " + maxSeconds + "]");
    }
}
