package com.menta.auth.infrastructure.security;

import com.menta.auth.domain.model.Role;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Path-prefix → required-role Spring Security 6 authorization manager
 * (ADR-0025 + design.md Decision 4).
 *
 * Construction receives an immutable path-to-roles map. At check time the
 * longest path-prefix wins (deterministic order, more specific paths
 * declared first by the application bootstrap).
 *
 * Fall-through semantics:
 *   - Path matches no entry → grant (the controller layer is responsible
 *     for finer-grained checks; this manager is the coarse gate).
 *   - Authentication missing or anonymous → deny for any protected path.
 *   - Authentication present with a matching ROLE_ authority → grant.
 */
@Component
public class RoleAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {

    private final Map<String, Set<Role>> pathToRoles;

    public RoleAuthorizationManager(Map<String, Set<Role>> pathToRoles) {
        // Defensive copy: callers mutate cannot disturb the manager.
        Map<String, Set<Role>> copy = new LinkedHashMap<>();
        pathToRoles.forEach((k, v) -> copy.put(k, Set.copyOf(v)));
        this.pathToRoles = Collections.unmodifiableMap(copy);
    }

    @Override
    public AuthorizationDecision check(
        Supplier<Authentication> authentication,
        RequestAuthorizationContext context
    ) {
        String requestUri = context.getRequest().getRequestURI();
        if (requestUri == null) {
            return new AuthorizationDecision(true);
        }
        Set<Role> requiredRoles = lookupRequiredRoles(requestUri);
        if (requiredRoles == null) {
            // No mapping → fall-through grant. Authenticated-route gating is
            // done in the controller layer / by SecurityFilterChain matchers.
            return new AuthorizationDecision(true);
        }

        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        Set<String> grantedAuthorities = authoritiesAsStrings(auth);
        for (Role required : requiredRoles) {
            if (grantedAuthorities.contains("ROLE_" + required.name())) {
                return new AuthorizationDecision(true);
            }
        }
        return new AuthorizationDecision(false);
    }

    private Set<Role> lookupRequiredRoles(String requestUri) {
        for (Map.Entry<String, Set<Role>> entry : pathToRoles.entrySet()) {
            String prefix = entry.getKey();
            if (!prefix.endsWith("/**")) {
                continue;
            }
            String basePrefix = prefix.substring(0, prefix.length() - 3);
            if (requestUri.equals(basePrefix) || requestUri.startsWith(basePrefix + "/")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Set<String> authoritiesAsStrings(Authentication auth) {
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(java.util.stream.Collectors.toSet());
    }
}
