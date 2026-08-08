package com.menta.bff.infrastructure.security;

import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.dto.TokenPairResponse;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.domain.model.SessionTokens;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Custom authentication provider that validates credentials against Auth API
 * and stores tokens in session.
 *
 * Flow:
 * 1. Spring Security calls authenticate() with email + password from form
 * 2. Call Auth API /api/v1/auth/login
 * 3. If success: store tokens in session + return authenticated principal
 * 4. If failure: throw BadCredentialsException
 *
 * This integrates with Spring Security's formLogin() mechanism.
 */
@Component
public class BffAuthenticationProvider implements AuthenticationProvider {

    private final AuthApiClient authApiClient;
    private final SessionTokenRepository sessionTokenRepository;

    public BffAuthenticationProvider(
        AuthApiClient authApiClient,
        SessionTokenRepository sessionTokenRepository
    ) {
        this.authApiClient = authApiClient;
        this.sessionTokenRepository = sessionTokenRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = authentication.getCredentials().toString();

        try {
            // Call Auth API
            LoginCommand command = new LoginCommand(email, password);
            TokenPairResponse response = authApiClient.login(command);

            // Store tokens in session
            SessionTokens sessionTokens = new SessionTokens(
                response.accessToken(),
                response.refreshToken(),
                Instant.now().plusSeconds(response.expiresIn())
            );
            sessionTokenRepository.store(sessionTokens);

            // Parse userId and role from access token (JWT)
            // TODO: Extract role from JWT claims instead of hardcoding
            // For MVP, we'll use ROLE_USER
            List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER")
            );

            // Return authenticated principal
            // Principal name = email (user-facing identifier)
            return new UsernamePasswordAuthenticationToken(
                email,
                null, // don't expose password
                authorities
            );

        } catch (com.menta.bff.application.port.out.AuthApiClient.AuthenticationException e) {
            throw new BadCredentialsException("Invalid email or password");
        } catch (com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException e) {
            throw new org.springframework.security.authentication.InternalAuthenticationServiceException(
                "Auth service temporarily unavailable"
            );
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
