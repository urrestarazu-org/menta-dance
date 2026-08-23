package com.menta.auth.infrastructure.security;

import com.menta.auth.domain.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for :api:auth.
 *
 * Wires the JWT bearer filter before UsernamePasswordAuthenticationFilter so
 * authenticated requests populate the SecurityContext, installs the
 * RoleAuthorizationManager as the path-prefix authorization manager, and
 * exposes default rules so public + authenticated routes have a stable
 * default-deny fail policy.
 *
 * Path policy:
 *   - /api/v1/auth/login and /api/v1/auth/refresh → permitAll (controllers handle credentials).
 *   - /api/v1/auth/logout → authenticated Bearer access token required.
 *   - /api/v1/auth/forgot-password and /api/v1/auth/reset-password → permitAll
 *     (US-AUTH-005/006; the reset token itself is the temporary authorization).
 *   - /api/v1/billing/plans and /api/v1/billing/plans/** → permitAll
 *     (US-BILLING-001; public plan catalog, no account required to browse).
 *   - GET /api/v1/billing/physical/courses/{courseId}/pricing → permitAll
 *     (US-BILLING-009; reading a course's current price requires no
 *     account). PUT on the same path is a separate rule below, restricted to
 *     ADMIN/INSTRUCTOR — ownership of the specific course is checked in the
 *     use case, not here.
 *   - POST /api/v1/billing/physical/quotes → authenticated (any role)
 *     (US-BILLING-006; a quote is not persisted for an anonymous caller. No
 *     specific role is required and no coarse role gate applies here — this
 *     matcher only proves the caller holds a valid Bearer token, since an
 *     unmapped path otherwise falls through to a grant via {@code
 *     RoleAuthorizationManager}).
 *   - POST /api/v1/billing/subscriptions → authenticated (any role)
 *     (US-BILLING-010, #116; the subscription and its payment are bound to
 *     the token's user — the client never says whose they are. Same
 *     explicit-matcher reasoning as the quotes rule above.)
 *   - /api/v1/catalog/courses and /api/v1/catalog/courses/** → permitAll
 *     (#95; public course catalog composed from Physical/Virtual, no
 *     account required to browse).
 *   - GET /api/v1/virtual/lessons and /api/v1/virtual/lessons/** → permitAll
 *     (#48, US-VIRTUAL-003; public lesson-detail read. Free lessons
 *     resolve without an account; callers with no entitlement on a
 *     premium lesson hit the public advice chain — anonymous → 403
 *     ProblemDetail, identified-but-not-subscribed → 200 with
 *     {@code access.allowed=false}).
 *   - /api/v1/billing/payments/mercadopago/webhook → permitAll
 *     (US-BILLING-002; Mercado Pago has no Bearer token — the controller's
 *     own HMAC signature verification is the authorization mechanism).
 *   - /actuator/health                          → permitAll
 *   - /api/v1/users/register                    → permitAll (public registration; PR2 contract)
 *   - /api/v1/admin/physical/courses/**         → ADMIN or INSTRUCTOR
 *     (#42; a PROFESOR manages their own courses under the admin prefix —
 *     evaluated before the generic /api/v1/admin/** rule below, since
 *     Spring evaluates authorizeHttpRequests matchers in declaration order
 *     and the first match wins. Resource-level ownership — an INSTRUCTOR
 *     touching a course they don't own — is checked in the use case, not
 *     here; this rule only proves the caller is one of the two roles that
 *     may reach the controller at all).
 *   - /api/v1/admin/physical/sessions/**        → ADMIN or INSTRUCTOR
 *     (#43; updating a session lives under a DIFFERENT top-level prefix
 *     than /admin/physical/courses/** above — that matcher does not cover
 *     it — so it needs its own rule, same reasoning and ordering).
 *   - POST /api/v1/physical/sessions/{sessionId}/access-qr → authenticated
 *     (any role) (US-PHYSICAL-001 escenario 1; a confirmed student requests
 *     their own ephemeral check-in QR — same explicit-matcher reasoning as
 *     #37's quotes rule, no specific role required).
 *   - POST /api/v1/physical/sessions/{sessionId}/check-ins → permitAll
 *     (US-PHYSICAL-001 escenario 2; the door reader has no user session — it
 *     authenticates with a shared device token the use case itself verifies,
 *     never the filter chain).
 *   - /api/v1/admin/virtual/courses/**          → ADMIN or INSTRUCTOR
 *   - /api/v1/admin/virtual/modules/**          → ADMIN or INSTRUCTOR
 *   - /api/v1/admin/virtual/lessons/**          → ADMIN or INSTRUCTOR
 *     (#54; course/module/lesson management each live under their own
 *     top-level prefix — same three-way split as Physical's courses vs.
 *     sessions, same first-match-wins ordering rationale. Ownership of the
 *     specific course a module/lesson belongs to is checked in the use
 *     case, never here.)
 *   - PUT /api/v1/billing/physical/courses/{courseId}/pricing → ADMIN or
 *     INSTRUCTOR (US-BILLING-009, #37; the GET permitAll rule above covers
 *     the same path for reads — Spring Security matches per-HTTP-method, so
 *     both rules apply to the same prefix without conflict. Ownership of
 *     the specific course is checked in the use case, never here.)
 *   - everything else under /api/v1/admin/**    → requires ADMIN authority
 *   - everything else under /api/v1/instructor/** → requires INSTRUCTOR authority
 *   - other authenticated paths                 → fall-through via RoleAuthorizationManager.
 *
 * The @EnableScheduling annotation lights up @Scheduled tasks so the
 * OutboxBlacklistReconciler (wired in :api:app) can drive its pull-batch
 * cadence without an external scheduler.
 */
@Configuration
@EnableWebSecurity
@EnableScheduling
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        RoleAuthorizationManager roleAuthorizationManager,
        ObjectMapper objectMapper
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Login and refresh authenticate their own credentials. Logout requires
                // a valid access Bearer token, while its refresh is supplied separately.
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/register",
                    "/api/v1/auth/activate/**",
                    "/api/v1/auth/resend-activation",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    "/api/v1/billing/plans",
                    "/api/v1/billing/plans/**",
                    "/api/v1/billing/payments/mercadopago/webhook",
                    "/api/v1/catalog/courses",
                    "/api/v1/catalog/courses/**",
                    "/api/v1/virtual/lessons",
                    "/api/v1/virtual/lessons/**"
                ).permitAll()
                // US-BILLING-009, #37: reading a course's current price requires no account.
                // The PUT rule below covers the same path for writes.
                .requestMatchers(
                    HttpMethod.GET, "/api/v1/billing/physical/courses/*/pricing"
                ).permitAll()
                .requestMatchers("/api/v1/auth/logout").authenticated()
                // US-BILLING-006: any authenticated user may request a quote — no role
                // restriction, and this path is otherwise unmapped so it would fall
                // through to a grant without this explicit rule.
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/physical/quotes").authenticated()
                // US-BILLING-010, #116: any authenticated user may open a subscription
                // checkout. Declared before the generic /api/v1/admin/** gate below and
                // before anyRequest(), same first-match-wins ordering as #37 and #34.
                // The owning user comes from the token, never from the body.
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/subscriptions").authenticated()
                // US-PHYSICAL-001 escenario 2: the door reader has no user session — it
                // authenticates with a shared device token the use case itself verifies.
                .requestMatchers(HttpMethod.POST, "/api/v1/physical/sessions/*/check-ins").permitAll()
                // US-PHYSICAL-001 escenario 1: any authenticated student may request their
                // own ephemeral check-in QR — no specific role required.
                .requestMatchers(HttpMethod.POST, "/api/v1/physical/sessions/*/access-qr").authenticated()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/users/register").permitAll()
                // #42: ADMIN and INSTRUCTOR share this prefix; ownership of a specific
                // course is enforced in the use case, not here. Must be declared before
                // the generic /api/v1/admin/** rule below — first match wins.
                .requestMatchers("/api/v1/admin/physical/courses/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                // #43: different top-level prefix than /courses/** above — needs its own
                // matcher, same first-match-wins ordering rationale.
                .requestMatchers("/api/v1/admin/physical/sessions/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                // #54: course/module/lesson management, each its own top-level prefix.
                .requestMatchers("/api/v1/admin/virtual/courses/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                .requestMatchers("/api/v1/admin/virtual/modules/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                .requestMatchers("/api/v1/admin/virtual/lessons/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                // US-BILLING-009, #37: PUT restricted to ADMIN/INSTRUCTOR; the GET rule
                // above already covers this same path for reads (per-HTTP-method matching).
                .requestMatchers(
                    HttpMethod.PUT, "/api/v1/billing/physical/courses/*/pricing"
                ).hasAnyRole("ADMIN", "INSTRUCTOR")
                // Coarse-grained role gates.
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/instructor/**").hasRole("INSTRUCTOR")
                // Anything else: defer to RoleAuthorizationManager (longest path-prefix wins,
                // unmapped paths fall through to a grant so controller-layer checks apply).
                .anyRequest().access(roleAuthorizationManager)
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(ProblemJsonSecurityHandlers.authenticationEntryPoint(objectMapper))
                .accessDeniedHandler(ProblemJsonSecurityHandlers.accessDeniedHandler(objectMapper))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RoleAuthorizationManager roleAuthorizationManager() {
        Map<String, Set<Role>> pathToRoles = new LinkedHashMap<>();
        pathToRoles.put("/api/v1/admin/**", Set.of(Role.ADMIN));
        pathToRoles.put("/api/v1/instructor/**", Set.of(Role.INSTRUCTOR));
        return new RoleAuthorizationManager(pathToRoles);
    }

    /**
     * UserDetailsService bridge — kept as the in-memory stub so Spring
     * Security's UserDetailsService contract has a bean. Real authentication
     * flows run via JwtAuthenticationFilter + TokenUserDetailsService (a
     * regular @Service bean).
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
