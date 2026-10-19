package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the door reader cannot be authenticated against the device registry (#266,
 * US-PHYSICAL-001 escenario 2): an unknown or malformed {@code deviceId}, an absent {@code
 * deviceToken}, or a secret that does not match. One class on purpose, so the caller cannot tell
 * the cases apart. {@code PhysicalDeviceAuthenticator} runs this very first in {@code
 * ProcessPhysicalCheckInUseCaseImpl} — before even attempting to parse {@code qrCredentials} —
 * because an unauthenticated reader has no business learning anything about the QR payload's
 * shape, a session's existence, or a student's assignment status.
 *
 * <p>The secret comparison runs in constant time. Never surface which reader, device id, or
 * secret prefix was wrong — the {@code @RestControllerAdvice} mapping returns a flat 401 with no
 * further detail.</p>
 */
public class InvalidDeviceTokenException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_DEVICE_TOKEN";

    public InvalidDeviceTokenException() {
        super(ERROR_CODE, "The device token is invalid.");
    }
}
