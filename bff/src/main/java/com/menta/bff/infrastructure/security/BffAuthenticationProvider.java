package com.menta.bff.infrastructure.security;

import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.LoginUseCase;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bridges Spring Security's {@code formLogin()} to the application layer.
 *
 * <p>This class owns exactly one concern: translating between Spring
 * Security's {@link Authentication} contract and the login use case. The flow
 * itself — exchange credentials at the Auth API, then store the token pair in
 * the server-side session so the browser only ever holds an opaque cookie —
 * belongs to {@link LoginUseCase} and is not restated here. Two copies of
 * token custody are two places to forget a change.</p>
 *
 * <p>Failure mapping is deliberate: invalid credentials and an unreachable
 * Auth API are different events. Reporting the latter as a bad password sends
 * users to reset a credential that was never wrong.</p>
 */
@Component
public class BffAuthenticationProvider implements AuthenticationProvider {

    private final LoginUseCase loginUseCase;

    public BffAuthenticationProvider(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = authentication.getCredentials().toString();

        try {
            loginUseCase.execute(new LoginCommand(email, password, clientAddressOf(authentication)));
        } catch (AuthApiClient.AuthenticationException e) {
            throw new BadCredentialsException("Invalid email or password");
        } catch (AuthApiClient.ServiceUnavailableException e) {
            throw new InternalAuthenticationServiceException("Auth service temporarily unavailable");
        }

        // TODO: Extract role from JWT claims instead of hardcoding
        // For MVP, we'll use ROLE_USER
        List<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER")
        );

        // Credentials are dropped: this token is stored in the session-backed
        // security context and would otherwise outlive the request that
        // carried the password.
        return new UsernamePasswordAuthenticationToken(email, null, authorities);
    }

    /**
     * Reads the origin captured by {@link ClientAuthenticationDetailsSource}.
     *
     * <p>Returns {@code null} when no details were attached — for instance on
     * a programmatic authentication that never passed through the filter. That
     * degrades to the Auth API observing the BFF as the peer, which is the
     * behaviour that existed before ADR-0035 and is strictly safer than
     * forwarding an origin we cannot vouch for.</p>
     */
    private static String clientAddressOf(Authentication authentication) {
        return authentication.getDetails() instanceof ClientAuthenticationDetails details
            ? details.clientAddress()
            : null;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
