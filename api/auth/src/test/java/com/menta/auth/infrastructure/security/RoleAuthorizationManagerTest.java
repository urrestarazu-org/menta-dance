package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.domain.model.Role;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * RED-GREEN discipline: this test references RoleAuthorizationManager BEFORE
 * 3.5 GREEN provides the impl, so it must not compile until the manager exists.
 *
 * Strict TDD: path-to-required-role matching is verified through the actual
 * AuthorizationDecision returned to the SecurityFilterChain. The map is
 * loaded once at construction and reused per call (immutable config is part
 * of the spec).
 */
class RoleAuthorizationManagerTest {

    private static final Map<String, Set<Role>> PATH_REQUIRED_ROLES = Map.of(
        "/api/v1/admin/**", Set.of(Role.ADMIN),
        "/api/v1/courses/**", Set.of(Role.ADMIN, Role.INSTRUCTOR),
        "/api/v1/me/**", Set.of(Role.ADMIN, Role.INSTRUCTOR, Role.STUDENT)
    );

    private AuthorizationManager<RequestAuthorizationContext> manager;

    @BeforeEach
    void setUp() {
        manager = new RoleAuthorizationManager(PATH_REQUIRED_ROLES);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Spec: Role-based access")
    class RoleMatching {

        @Test
        void grants_when_role_matches_a_required_role() {
            SecurityContextHolder.getContext().setAuthentication(
                authenticationFor(Role.INSTRUCTOR));
            assertThat(check("/api/v1/courses/abc").isGranted()).isTrue();
        }

        @Test
        void denies_when_role_does_not_match_any_required_role() {
            SecurityContextHolder.getContext().setAuthentication(
                authenticationFor(Role.STUDENT));
            assertThat(check("/api/v1/admin/users").isGranted()).isFalse();
        }

        @Test
        void grants_when_path_has_no_required_role_mapping() {
            // Public-style paths fall through to allow (controller can decide).
            SecurityContextHolder.getContext().setAuthentication(anonymous());
            assertThat(check("/api/v1/public/info").isGranted()).isTrue();
        }

        @Test
        void denies_anonymous_access_to_protected_path() {
            SecurityContextHolder.getContext().setAuthentication(anonymous());
            assertThat(check("/api/v1/courses/abc").isGranted()).isFalse();
        }

        @Test
        void grants_admin_role_for_admin_path() {
            SecurityContextHolder.getContext().setAuthentication(
                authenticationFor(Role.ADMIN));
            assertThat(check("/api/v1/admin/users/1").isGranted()).isTrue();
        }

        @Test
        void grants_student_role_for_me_path() {
            SecurityContextHolder.getContext().setAuthentication(
                authenticationFor(Role.STUDENT));
            assertThat(check("/api/v1/me/profile").isGranted()).isTrue();
        }
    }

    private AuthorizationDecision check(String path) {
        // Spring's MockHttpServletRequest implements HttpServletRequest, so
        // the AuthorizationContext can be constructed without a stub.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);

        Supplier<Authentication> authSupplier =
            () -> SecurityContextHolder.getContext().getAuthentication();
        var context = new RequestAuthorizationContext(request);
        return manager.check(authSupplier, context);
    }

    private Authentication authenticationFor(Role role) {
        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + role.name())
        );
        return new UsernamePasswordAuthenticationToken(
            "user-id-" + role.name(), null, authorities);
    }

    private Authentication anonymous() {
        return new AnonymousAuthenticationToken(
            "key", "anonymous",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
    }
}
