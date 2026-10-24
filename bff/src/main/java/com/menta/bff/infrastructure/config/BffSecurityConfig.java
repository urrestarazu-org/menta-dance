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
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Security configuration for BFF.
 *
 * <p>Configures:
 * - Custom AuthenticationProvider (validates against Auth API)
 * - Form-based login (custom login page); only the subscribe entry path is remembered across it
 * - Custom LogoutHandler (revokes refresh token BEFORE session invalidation)
 * - Custom LogoutSuccessHandler (redirects AFTER session invalidation)
 * - CSRF protection (session-stored tokens; only /actuator/health is exempt)
 * - Session management (CREATE_IF_REQUIRED)
 * - Public endpoints (/, /login, /registro, /activar, /reenviar-activacion,
 *   /recuperar-contrasena, /restablecer-contrasena, /classes and one-segment
 *   /classes/{id}, /actuator/health, /error)
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
                        // Compiled Tailwind stylesheet (#536): the login and onboarding pages
                        // are anonymous, so their CSS must be too. GET-only and scoped to
                        // /css/**, so no other static location and no mutating verb opens up.
                        .requestMatchers(HttpMethod.GET, "/css/**").permitAll()
                        // Self-hosted Poppins (woff2) referenced by app.css via @font-face;
                        // same GET-only, path-scoped reasoning as /css/**.
                        .requestMatchers(HttpMethod.GET, "/fonts/**").permitAll()
                        // Brand logo and hero photo (#649) shown on the anonymous home and
                        // login pages; same GET-only, path-scoped reasoning as /css/**.
                        .requestMatchers(HttpMethod.GET, "/images/**").permitAll()
                        // Legal and contact pages (#650): anonymous, one exact path each and GET-only.
                        .requestMatchers(HttpMethod.GET, "/terminos-condiciones", "/politica-privacidad",
                                "/politica-cookies", "/politica-devolucion", "/propiedad-intelectual",
                                "/accesibilidad", "/contacto").permitAll()
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
                        // Physical classes (#383): the list and one class page, GET-only and
                        // exact ("*" does not cross "/"), so subpaths and mutating verbs stay closed.
                        .requestMatchers(HttpMethod.GET, "/classes", "/classes/*").permitAll()
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
                        // Password recovery (#360): exact paths with explicit verbs. The reset
                        // link takes one path segment for the token; the bare path via GET and
                        // every other verb stay authenticated.
                        .requestMatchers(HttpMethod.GET, "/recuperar-contrasena", "/recuperar-contrasena/enviado")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/recuperar-contrasena").permitAll()
                        .requestMatchers(HttpMethod.GET, "/restablecer-contrasena/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/restablecer-contrasena").permitAll()
                        // Subscription checkout (#361): students only. Any other role gets 403
                        // before the controller runs; anonymous callers go to /login.
                        .requestMatchers(HttpMethod.POST, "/suscripciones").hasRole("STUDENT")
                        // Physical class purchase (#365): POST /classes/{id}/cotizar, POST /compras and
                        // GET /compras/retorno are deliberately not listed above, so they fall under
                        // anyRequest().authenticated();
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
                        // Only the anonymous "Suscribirme" entry point is remembered across the
                        // login (see subscribeEntryRequestCache); without alwaysUse, any other
                        // login still lands on the dashboard.
                        .defaultSuccessUrl("/dashboard")
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                // Login return (#361): the saved request is honored only for the subscribe entry path
                .requestCache(cache -> cache.requestCache(subscribeEntryRequestCache()))

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

    /**
     * Request cache that remembers only {@code GET /plans/suscribirme}. Every other request
     * that reaches the login is not saved, and there is no user-supplied return target, so
     * the login cannot be turned into an open redirect.
     */
    private static HttpSessionRequestCache subscribeEntryRequestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(PathPatternRequestMatcher.withDefaults()
                .matcher(HttpMethod.GET, "/plans/suscribirme"));
        return cache;
    }
}
