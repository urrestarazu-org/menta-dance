package com.menta.auth.infrastructure.transaction;

import com.menta.auth.application.dto.ChangeUserRoleCommand;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.in.ChangeUserRoleUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link ChangeUserRoleUseCase}.
 *
 * <p>The row locks that serialize role changes last until this transaction ends, and the role change, the
 * session revocation, the outbox event and the audit entry must commit together or not at all.
 */
public class TransactionalChangeUserRoleUseCase implements ChangeUserRoleUseCase {

    private final ChangeUserRoleUseCase delegate;

    public TransactionalChangeUserRoleUseCase(ChangeUserRoleUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public UserSummary changeRole(ChangeUserRoleCommand command) {
        return delegate.changeRole(command);
    }
}
