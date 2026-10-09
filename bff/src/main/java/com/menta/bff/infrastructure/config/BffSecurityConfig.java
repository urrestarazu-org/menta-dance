package com.menta.bff.infrastructure.config;

import com.menta.bff.infrastructure.security.BffAuthenticationProvider;
import com.menta.bff.infrastructure.security.BffLogoutHandler;
import com.menta.bff.infrastructure.security.BffLogoutSuccessHandler;
import com.menta.bff.infrastructure.security.ClientAuthenticationDetailsSource;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for BFF.
 *
 * <p>Configures:
 * - Custom AuthenticationProvider (validates against Auth API)
 * - Form-based login (custom login page)
 * - Custom LogoutHandler (revokes refresh token BEFORE session invalidation)
 * - Custom LogoutSuccessHandler (redirects AFTER session invalidation)
 * - CSRF protection (session-stored tokens; only /actuator/health is exempt)
 * - Session management (CREATE_IF_REQUIRED)
 * - Public endpoints (/, /login, /registro, /activar, /reenviar-activacion, /classes, /actuator/health,
 *   /error)
 * - Protected endpoints (all others require authentication)
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class BffSecurityConfig {

    private final BffAuthenticationProvider authenticationProvider;
    private final BffLogoutHandler logoutHandler;
    private final BffLogoutSuccessHandler logoutSuccessHandler;
    private final TokenRefreshFilter tokenRefreshFilter;
    private final ClientAuthenticationDetailsSource clientAuthenticationDetailsSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Custom authentication provider (validates against Auth API)
                .authenticationProvider(authenticationProvider)

                // Authorization rules
                .authorizeHttpRequests(authorize -> authorize
                        // Public endpoints - no authentication required
                        .requestMatchers("/login", "/error", "/actuator/health").permitAll()
                        // Course detail is access-agnostic (design D2, #170) — a single
                        // "*" does not cross "/", so future subpaths (e.g. a course's
                        // settings page) stay authenticated by default, and GET-only
                        // keeps mutating verbs closed (design decision G).
                        .requestMatchers(HttpMethod.GET, "/courses/*").permitAll()
                        // Lesson player is equally access-agnostic at the route level
                        // (design D2/G, #170): the controller forwards whatever token is
                        // present, and GetLessonViewUseCase renders the upstream access
                        // decision. Method-scoped GET-only, same reasoning as above.
                        .requestMatchers(HttpMethod.GET, "/courses/*/lessons/*").permitAll()
                        // Plans listing is public and caller-agnostic (design
                        // decision "GET /plans, no wildcard", #177): exact
                        // path only — the route takes no path variable, so a
                        // wildcard would also permit a future /plans/{id}
                        // that this change never intended to open.
                        .requestMatchers(HttpMethod.GET, "/plans").permitAll()
                        // Home page is the public course catalog (#358): exact "/" only,
                        // GET-only, so nothing beneath it or any mutating verb opens up.
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        // Physical classes list (#383): exact path, GET-only, so nothing
                        // beneath it and no mutating verb opens up.
                        .requestMatchers(HttpMethod.GET, "/classes").permitAll()
                        // Self-service account onboarding (#359): exact paths with explicit
                        // verbs, so nothing beneath them and no other verb opens up.
                        .requestMatchers(HttpMethod.GET, "/registro", "/registro/pendiente").permitAll()
                        .requestMatchers(HttpMethod.POST, "/registro").permitAll()
                        // Resend of the activation email (#359): exact path, GET + POST only.
                        .requestMatchers(HttpMethod.GET, "/reenviar-activacion").permitAll()
                        .requestMatchers(HttpMethod.POST, "/reenviar-activacion").permitAll()
                        // Activation link (#359): a single path segment for the token, so
                        // "/activar/a/b" and the bare "/activar" via GET stay authenticated;
                        // the token goes back in the body of POST /activar (PRG).
                        .requestMatchers(HttpMethod.GET, "/activar/*", "/cuenta-activada").permitAll()
                        .requestMatchers(HttpMethod.POST, "/activar").permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Form login configuration
                .formLogin(form -> form
                        .loginPage("/login")
                        // login.html submits the credential as "email"; Spring's
                        // default parameter is "username", which left the
                        // provider an empty email and made every browser login a 500 (#490).
                        .usernameParameter("email")
                        // ADR-0035: capture the canonical client origin while
                        // the request is still in hand, so the provider never
                        // has to reach for it out of band.
                        .authenticationDetailsSource(clientAuthenticationDetailsSource)
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                // Logout configuration with custom handlers
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        // Order of execution:
                        // 1. BffLogoutHandler runs FIRST (revokes refresh token while session is still valid)
                        // 2. Spring Security invalidates session (automatic)
                        // 3. BffLogoutSuccessHandler runs LAST (redirects to /login?logout)
                        .addLogoutHandler(logoutHandler) // Execute BEFORE session invalidation
                        .logoutSuccessHandler(logoutSuccessHandler) // Execute AFTER session invalidation
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION")
                        .permitAll()
                )

                // CSRF protection: every state-changing request (including /login
                // and /logout) needs the token Thymeleaf injects into th:action forms.
                // Local testing with curl/Bruno must GET /login first and send the token back.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/actuator/health"))

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
