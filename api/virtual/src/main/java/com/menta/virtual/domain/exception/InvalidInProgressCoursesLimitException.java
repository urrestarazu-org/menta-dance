package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/** Thrown when the in-progress courses listing is asked for a size outside its allowed range (#765). */
public class InvalidInProgressCoursesLimitException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_REQUEST";

    public InvalidInProgressCoursesLimitException(int limit, int maxLimit) {
        super(ERROR_CODE, "Limit " + limit + " is out of bounds [1, " + maxLimit + "]");
    }
}
