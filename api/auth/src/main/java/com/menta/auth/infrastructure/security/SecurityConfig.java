package com.menta.auth.infrastructure.security;

import com.menta.auth.domain.model.Role;

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
 * Path policy (PR3 wire-up):
 *   - /auth/login, /auth/refresh, /auth/logout  → permitAll (controllers handle auth itself).
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
        RoleAuthorizationManager roleAuthorizationManager
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints — the controllers reject invalid creds.
                .requestMatchers("/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/users/register").permitAll()
                // Coarse-grained role gates.
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/instructor/**").hasRole("INSTRUCTOR")
                // Anything else: defer to RoleAuthorizationManager (longest path-prefix wins,
                // unmapped paths fall through to a grant so controller-layer checks apply).
                .anyRequest().access(roleAuthorizationManager)
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
