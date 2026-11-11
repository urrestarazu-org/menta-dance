package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.ChangeUserRoleCommand;
import com.menta.auth.application.dto.UserSummary;

/** Application-layer entry point for an administrator changing a user's role. */
public interface ChangeUserRoleUseCase {

    /**
     * Gives the user a new role and closes every session that carries the old one.
     *
     * <p>Assigning the role the user already has is a no-op that returns the user untouched. The system must
     * keep at least one active administrator: demoting the last one is rejected.
     *
     * @return the user after the change
     * @throws com.menta.auth.domain.exception.UserNotFoundException when the user does not exist
     * @throws com.menta.auth.domain.exception.LastAdminCannotBeDemotedException when the change would leave the
     *     system without an active administrator
     */
    UserSummary changeRole(ChangeUserRoleCommand command);
}
