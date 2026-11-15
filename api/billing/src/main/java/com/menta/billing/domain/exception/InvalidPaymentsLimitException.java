package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/** Thrown when the student's own payments listing is asked for a size outside its allowed range (#767). */
public class InvalidPaymentsLimitException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_REQUEST";

    public InvalidPaymentsLimitException(int limit, int maxLimit) {
        super(ERROR_CODE, "Limit " + limit + " is out of bounds [1, " + maxLimit + "]");
    }
}
