package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown by QR check-in device authentication (#266) when the proven {@code ACTIVE} device has
 * reached its {@code expiresAt} (inclusive). An expired device is not recoverable by rotation; it
 * must be re-registered.
 */
public class DeviceExpiredException extends BusinessException {

    private static final String ERROR_CODE = "DEVICE_EXPIRED";

    /** Creates the exception with its fixed {@code DEVICE_EXPIRED} code. */
    public DeviceExpiredException() {
        super(ERROR_CODE, "The device has expired.");
    }
}
