package com.menta.auth.infrastructure.security;

import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AccessTokenIssuer.ParsedAccessToken;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer-token filter for requests to protected endpoints.
 *
 * Order of operations:
 *   1. Extract `Authorization: Bearer <token>`.
 *   2. Verify with the AccessTokenIssuer (signature + expiration + role +
 *      tokenVersion claim set).
 *   3. On success, populate the SecurityContext with a Username-based
 *      Authentication whose authorities list carries ROLE_<role>. The
 *      principal name is the userId UUID so downstream code can fetch the
 *      user from UserRepository if needed.
 *   4. On missing header, malformed header, or invalid token, leave the
 *      SecurityContext empty. The downstream chain decides — controllers
 *      mapped to permitAll() continue; protected routes trigger 401.
 *
 * The filter does NOT throw on bad credentials. Throwing here would skip
 * the chain and surface 500 for unauthenticated traffic; we let auth
 * happen via the standard entry point.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenIssuer accessTokenIssuer;

    public JwtAuthenticationFilter(AccessTokenIssuer accessTokenIssuer) {
        this.accessTokenIssuer = accessTokenIssuer;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        Optional<String> bearer = extractBearerToken(request);
        if (bearer.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Optional<ParsedAccessToken> parsed = accessTokenIssuer.parse(bearer.get());
        if (parsed.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication = buildAuthentication(parsed.get());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private static Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(token);
    }

    private static Authentication buildAuthentication(ParsedAccessToken claims) {
        List<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + claims.role().name())
        );
        return new UsernamePasswordAuthenticationToken(
            claims.userId().toString(),
            null,
            authorities
        );
    }
}
