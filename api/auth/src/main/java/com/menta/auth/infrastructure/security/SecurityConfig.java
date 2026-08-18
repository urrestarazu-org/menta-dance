package com.menta.auth.infrastructure.security;

import com.menta.auth.domain.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 *   - /actuator/health                          → permitAll
 *   - /api/v1/users/register                    → permitAll (public registration; PR2 contract)
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
                    "/api/v1/billing/plans/**"
                ).permitAll()
                .requestMatchers("/api/v1/auth/logout").authenticated()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/users/register").permitAll()
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
