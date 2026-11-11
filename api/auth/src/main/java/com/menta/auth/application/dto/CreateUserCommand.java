package com.menta.auth.application.dto;

import com.menta.auth.domain.model.Role;
import java.util.Objects;
import java.util.UUID;

/**
 * Request for an administrator to create an account for someone else.
 *
 * <p>The email, the names and the role are validated by the use case. {@link #toString()} never prints the
 * personal data.
 *
 * @param actorId the administrator creating the account, recorded in the audit trail
 * @param email the address the activation link is sent to
 * @param firstName the holder's given name
 * @param lastName the holder's family name
 * @param role the role of the new account
 */
public record CreateUserCommand(UUID actorId, String email, String firstName, String lastName, Role role) {

    /**
     * Validates that every component is present.
     *
     * @throws NullPointerException when any component is null
     */
    public CreateUserCommand {
        Objects.requireNonNull(actorId, "actorId cannot be null");
        Objects.requireNonNull(email, "email cannot be null");
        Objects.requireNonNull(firstName, "firstName cannot be null");
        Objects.requireNonNull(lastName, "lastName cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
    }

    @Override
    public String toString() {
        return "CreateUserCommand{role=" + role + ", profile=***}";
    }
}
