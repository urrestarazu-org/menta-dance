package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.ResendActivationCommand;
import com.menta.auth.application.dto.ResendActivationResult;

/**
 * Application-layer entry point for the non-enumerating resend-activation
 * flow (auth-account-activation spec: "Reenvío no enumerativo").
 *
 * <p>Returns the identical {@link ResendActivationResult} regardless of
 * whether the target email is unknown, already active, or pending — only a
 * pending account causes a persistence side-effect.</p>
 */
public interface ResendActivationUseCase {

    ResendActivationResult resend(ResendActivationCommand command);
}
