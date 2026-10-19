package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when {@code revoke()} is called on an already-{@code REVOKED} device (#44,
 * US-PHYSICAL-007, D5 — {@code REVOKED} is an absorbing state).
 */
public class DeviceAlreadyRevokedException extends BusinessException {

    private static final String ERROR_CODE = "DEVICE_ALREADY_REVOKED";

    public DeviceAlreadyRevokedException() {
        super(ERROR_CODE, "Device is already revoked");
    }
}
