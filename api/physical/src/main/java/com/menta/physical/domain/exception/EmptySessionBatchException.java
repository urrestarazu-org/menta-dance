package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a batch creation would generate no session: its range is inverted or holds no date
 * on the course's day of week (#743). Rejecting it makes a mistaken range visible instead of
 * answering with an empty list.
 */
public class EmptySessionBatchException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_REQUEST";

    public EmptySessionBatchException() {
        super(ERROR_CODE, "The range holds no date on the course's day of week");
    }
}
