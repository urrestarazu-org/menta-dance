package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the {@code qrCredentials} payload decoded correctly but its
 * embedded {@code expiresAtEpochSeconds} is already in the past at the time
 * the door reader scans it (US-PHYSICAL-001 escenario 4).
 *
 * <p>This is a separate outcome from {@link InvalidQrCredentialException}:
 * a well-formed-but-expired credential proves the student <em>was</em>
 * entitled to a seat (the signature matched), they simply presented it
 * after the issuer-bounded window. Surfacing it as INVALID_QR_CREDENTIAL
 * would hide that distinction and make replay/anomaly detection harder
 * once HMAC is wired in a follow-up.</p>
 */
public class ExpiredQrCredentialException extends BusinessException {

    private static final String ERROR_CODE = "EXPIRED_QR_CREDENTIAL";

    public ExpiredQrCredentialException() {
        super(ERROR_CODE, "The QR credential has expired.");
    }
}
