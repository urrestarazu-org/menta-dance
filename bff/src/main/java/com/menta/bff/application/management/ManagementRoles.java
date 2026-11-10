package com.menta.bff.application.management;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role vocabulary of the management area.
 *
 * <p>The only place where roles coming from Spring Security (authorities such as {@code ROLE_ADMIN})
 * are normalized to the bare names the Auth API uses, and where "staff" is defined.
 * </p>
 */
public final class ManagementRoles {

    /** Prefix Spring Security puts in front of a role name in an authority. */
    private static final String AUTHORITY_PREFIX = "ROLE_";

    /** Roles that work in the management area; everyone else is a regular user. */
    private static final Set<String> STAFF = Set.of("ADMIN", "INSTRUCTOR", "RECEPTIONIST");

    private ManagementRoles() {
    }

    /**
     * Strips the {@code ROLE_} prefix and drops null and blank entries.
     *
     * @param roles role names or authorities; may be {@code null}
     * @return the bare role names, never {@code null}
     */
    public static Set<String> normalize(Collection<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .map(role -> role.startsWith(AUTHORITY_PREFIX) ? role.substring(AUTHORITY_PREFIX.length()) : role)
                .filter(role -> !role.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether any of the roles belongs to the staff that works in the management area.
     *
     * @param roles role names or authorities; may be {@code null}
     * @return {@code true} for an administrator, an instructor or a receptionist
     */
    public static boolean isStaff(Collection<String> roles) {
        return normalize(roles).stream().anyMatch(STAFF::contains);
    }
}
