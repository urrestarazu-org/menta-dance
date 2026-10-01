package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown by QR check-in device authentication (#266) when the proven device is {@code REVOKED}.
 *
 * <p>Deliberately distinct from {@link DeviceRevokedException}, which means "admin tried to rotate
 * the secret of a revoked device" and maps to 409. Both share the {@code DEVICE_REVOKED} code; the
 * annotation-scoped advices give each its own status (401 here, 409 there).</p>
 */
public class CheckInDeviceRevokedException extends BusinessException {

    private static final String ERROR_CODE = "DEVICE_REVOKED";

    /** Creates the exception with its fixed {@code DEVICE_REVOKED} code. */
    public CheckInDeviceRevokedException() {
        super(ERROR_CODE, "The device is revoked.");
    }
}
