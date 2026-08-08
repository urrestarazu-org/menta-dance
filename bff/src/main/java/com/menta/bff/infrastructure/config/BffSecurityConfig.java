package com.menta.bff.infrastructure.config;

import com.menta.bff.infrastructure.security.BffAuthenticationProvider;
import com.menta.bff.infrastructure.security.BffLogoutSuccessHandler;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for BFF.
 * <p>
 * Configures:
 * - Custom AuthenticationProvider (validates against Auth API)
 * - Form-based login (custom login page)
 * - Custom LogoutSuccessHandler (revokes refresh token in Auth API)
 * - CSRF protection (enabled for state-changing operations)
 * - Session management (CREATE_IF_REQUIRED)
 * - Public endpoints (/login, /actuator/health, /error)
 * - Protected endpoints (all others require authentication)
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class BffSecurityConfig {

    private final BffAuthenticationProvider authenticationProvider;
    private final BffLogoutSuccessHandler logoutSuccessHandler;
    private final TokenRefreshFilter tokenRefreshFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Custom authentication provider (validates against Auth API)
                .authenticationProvider(authenticationProvider)

                // Authorization rules
                .authorizeHttpRequests(authorize -> authorize
                        // Public endpoints - no authentication required
                        .requestMatchers("/login", "/error", "/actuator/health").permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Form login configuration
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                // Logout configuration with custom handler
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler) // Custom handler revokes refresh token
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION")
                        .permitAll()
                )

                // CSRF protection (enabled by default for POST/PUT/DELETE)
                // GET/HEAD/OPTIONS/TRACE are safe methods (no CSRF needed)

                // Session management
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().changeSessionId() // Mitigate session fixation attacks
                        .maximumSessions(1) // Only one session per user
                        .maxSessionsPreventsLogin(false) // Invalidate old session when new login
                )

                // Token refresh filter (transparent access token refresh)
                // Runs BEFORE UsernamePasswordAuthenticationFilter to ensure tokens are refreshed
                // before business logic executes
                .addFilterBefore(tokenRefreshFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
