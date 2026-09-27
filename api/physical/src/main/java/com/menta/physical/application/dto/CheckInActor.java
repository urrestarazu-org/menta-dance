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

    public static CheckInActor anonymous() {
        return ANONYMOUS;
    }

    public boolean hasAnyRoleOf(Set<String> allowed) {
        return roleNames.stream().anyMatch(allowed::contains);
    }
}
