package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the instant range of a staff session listing is not acceptable: it is empty or inverted
 * ({@code from} not before {@code to}), or wider than the maximum the listing allows (#355). The
 * message is fixed and carries no request data.
 */
public class InvalidSessionRangeException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_REQUEST";

    public InvalidSessionRangeException() {
        super(ERROR_CODE, "The session range must have from before to and span at most 7 days");
    }
}
