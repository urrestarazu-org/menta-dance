package com.menta.auth.application.dto;

import com.menta.auth.domain.model.Role;
import java.util.Objects;
import java.util.UUID;

/**
 * Request to give a user a different role.
 *
 * @param actorId the administrator making the change, recorded in the audit trail
 * @param targetUserId the user whose role changes
 * @param newRole the role to assign
 */
public record ChangeUserRoleCommand(UUID actorId, UUID targetUserId, Role newRole) {

    /**
     * Validates that every component is present.
     *
     * @throws NullPointerException when any component is null
     */
    public ChangeUserRoleCommand {
        Objects.requireNonNull(actorId, "actorId cannot be null");
        Objects.requireNonNull(targetUserId, "targetUserId cannot be null");
        Objects.requireNonNull(newRole, "newRole cannot be null");
    }
}
