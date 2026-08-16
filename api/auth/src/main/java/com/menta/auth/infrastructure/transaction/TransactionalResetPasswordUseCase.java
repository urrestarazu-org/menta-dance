package com.menta.auth.infrastructure.transaction;

import com.menta.auth.application.dto.ResetPasswordCommand;
import com.menta.auth.application.port.in.ResetPasswordUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for ResetPasswordUseCase.
 *
 * <p>Token consumption, the password change with its tokenVersion bump, and
 * the revocation of every refresh token must commit together. A partial
 * commit here is a security failure, not just an inconsistency: a consumed
 * token with an unchanged password locks the user out, and a changed password
 * with surviving refresh tokens leaves the old sessions alive (US-AUTH-006
 * escenario 1).</p>
 */
public class TransactionalResetPasswordUseCase implements ResetPasswordUseCase {

    private final ResetPasswordUseCase delegate;

    public TransactionalResetPasswordUseCase(ResetPasswordUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void reset(ResetPasswordCommand command) {
        delegate.reset(command);
    }
}
