package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.ResetPasswordCommand;

/** Application-layer entry point for consuming a reset token (US-AUTH-006). */
public interface ResetPasswordUseCase {

    /**
     * Sets a new password using a reset token, closing every existing session of the account.
     *
     * <p>A rejected new password (weak, or equal to the current one) leaves the token usable so the caller can
     * retry with the same link.
     *
     * @param command carries the raw reset token, the new password and the client fingerprint used for throttling
     */
    void reset(ResetPasswordCommand command);
}
