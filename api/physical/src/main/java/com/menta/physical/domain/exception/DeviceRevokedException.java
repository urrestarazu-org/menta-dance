package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when {@code rotateSecret()} is called on a {@code REVOKED} device (#44,
 * US-PHYSICAL-007, D5 — {@code REVOKED} is an absorbing state). Deliberately distinct from
 * {@link DeviceAlreadyRevokedException} so revoke-after-revoke and rotate-after-revoke are two
 * independently assertable outcomes.
 */
public class DeviceRevokedException extends BusinessException {

    private static final String ERROR_CODE = "DEVICE_REVOKED";

    public DeviceRevokedException() {
        super(ERROR_CODE, "Device is revoked");
    }
}
