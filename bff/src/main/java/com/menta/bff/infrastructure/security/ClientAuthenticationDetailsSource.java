package com.menta.bff.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.stereotype.Component;

/**
 * Captures the canonical client origin at the moment Spring Security builds
 * the form-login {@code Authentication} (ADR-0035).
 *
 * <p>This is the supported extension point for attaching request-derived data
 * to an authentication attempt, which is why it is preferred over reaching for
 * the request from inside the provider.</p>
 */
@Component
public class ClientAuthenticationDetailsSource
    implements AuthenticationDetailsSource<HttpServletRequest, ClientAuthenticationDetails> {

    private final ClientOriginResolver clientOriginResolver;

    public ClientAuthenticationDetailsSource(ClientOriginResolver clientOriginResolver) {
        this.clientOriginResolver = clientOriginResolver;
    }

    @Override
    public ClientAuthenticationDetails buildDetails(HttpServletRequest request) {
        return new ClientAuthenticationDetails(clientOriginResolver.resolve(request));
    }
}
