package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.ResetPasswordCommand;

/** Application-layer entry point for consuming a reset token (US-AUTH-006). */
public interface ResetPasswordUseCase {

    void reset(ResetPasswordCommand command);
}
