package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.RequestPasswordResetCommand;
import com.menta.auth.application.dto.RequestPasswordResetResult;

/**
 * Application-layer entry point for the non-enumerating forgot-password flow
 * (US-AUTH-005).
 *
 * <p>Returns the identical {@link RequestPasswordResetResult} regardless of
 * whether the target email is unknown, not active, or active — only an
 * active account causes a persistence side-effect.</p>
 */
public interface RequestPasswordResetUseCase {

    /**
     * Requests a password reset for the given email without revealing whether the account exists.
     *
     * @param command carries the email and the client fingerprint used for throttling
     * @return always the same acknowledgement, for unknown, inactive and active accounts alike
     */
    RequestPasswordResetResult request(RequestPasswordResetCommand command);
}
