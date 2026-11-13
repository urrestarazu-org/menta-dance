package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the instant range of a read is not acceptable: it is empty or inverted ({@code from} not
 * before {@code to}), or wider than the maximum that read allows (#355, #729). The message is fixed per
 * maximum and carries no request data.
 */
public class InvalidSessionRangeException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_REQUEST";

    private final long maxDays;

    public InvalidSessionRangeException(long maxDays) {
        super(ERROR_CODE, "The range must have from before to and span at most " + maxDays + " days");
        this.maxDays = maxDays;
    }

    /** The widest span, in days, the failed read accepts. */
    public long getMaxDays() {
        return maxDays;
    }
}
