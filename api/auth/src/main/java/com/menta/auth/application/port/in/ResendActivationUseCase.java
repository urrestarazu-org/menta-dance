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

    /**
     * Issues a fresh activation token for a pending account without revealing whether the account exists.
     *
     * <p>Any previously issued active token of the account is invalidated first.
     *
     * @param command carries the email and the client fingerprint used for throttling
     * @return always the same acknowledgement, for unknown, active and pending accounts alike
     */
    ResendActivationResult resend(ResendActivationCommand command);
}
