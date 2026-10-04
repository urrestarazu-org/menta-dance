package com.menta.physical.application.dto;

import java.util.Set;
import java.util.UUID;

/**
 * The caller attempting a check-in, expressed as plain role names rather than
 * {@code com.menta.auth.domain.model.Role} (#45, US-PHYSICAL-008):
 * {@code :api:physical} does not declare {@code :api:auth} on its compile
 * classpath, so role names cross the module boundary the same way they
 * already do through the JWT {@code role} claim. An anonymous caller is a
 * real, representable case — {@link #anonymous()} — never {@code null}.
 */
public record CheckInActor(UUID userId, Set<String> roleNames) {

    private static final CheckInActor ANONYMOUS = new CheckInActor(null, Set.of());

    public CheckInActor {
        roleNames = roleNames == null ? Set.of() : Set.copyOf(roleNames);
        if (!roleNames.isEmpty() && userId == null) {
            throw new IllegalArgumentException("an authenticated actor must carry a userId");
        }
    }

    /**
     * Returns the shared anonymous actor: no user id and no roles.
     *
     * <p>Used for QR scans, where the caller is a door reader and not a person.
     */
    public static CheckInActor anonymous() {
        return ANONYMOUS;
    }

    /**
     * Tells whether this actor holds at least one of the given role names.
     *
     * <p>Names are compared as exact strings; an anonymous actor never matches.
     */
    public boolean hasAnyRoleOf(Set<String> allowed) {
        return roleNames.stream().anyMatch(allowed::contains);
    }
}
