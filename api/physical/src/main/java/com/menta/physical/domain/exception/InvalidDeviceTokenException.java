package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the door reader's {@code deviceToken} does not match the
 * configured shared secret (US-PHYSICAL-001 escenario 2). This is the very
 * first check {@code ProcessPhysicalCheckInUseCaseImpl} performs — before
 * even attempting to parse {@code qrCredentials} — because an unauthenticated
 * reader has no business learning anything about the QR payload's shape,
 * a session's existence, or a student's assignment status.
 *
 * <p>The comparison itself must run in constant time: a reader guessing the
 * secret byte-by-byte via response-time differences would otherwise have a
 * side channel. Never surface which reader, device id, or secret prefix was
 * wrong — the {@code @RestControllerAdvice} mapping returns a flat 401 with
 * no further detail.</p>
 */
public class InvalidDeviceTokenException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_DEVICE_TOKEN";

    public InvalidDeviceTokenException() {
        super(ERROR_CODE, "The device token is invalid.");
    }
}
