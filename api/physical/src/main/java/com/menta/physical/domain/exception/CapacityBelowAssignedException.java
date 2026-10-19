package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a session's capacity is reduced below its already-confirmed
 * {@code assignedSpots} (US-PHYSICAL-006 escenario 5).
 */
public class CapacityBelowAssignedException extends BusinessException {

    private static final String ERROR_CODE = "CAPACITY_BELOW_ASSIGNED";

    public CapacityBelowAssignedException() {
        super(ERROR_CODE, "New capacity is below the number of already assigned spots");
    }
}
