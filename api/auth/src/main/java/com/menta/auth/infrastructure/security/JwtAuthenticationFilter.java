package com.menta.auth.infrastructure.security;

import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AccessTokenIssuer.ParsedAccessToken;
import com.menta.auth.application.port.out.TokenBlacklistPort;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   3. Verify the token has not been revoked since it was issued (#88):
 *      its jti must not be blacklisted, and its tokenVersion claim must not
 *      be older than the version projected in Redis. Signature validity alone
 *      says nothing about revocation — before this check a logged-out or
 *      password-reset token stayed usable for its full 15-minute lifetime.
 *   4. On success, populate the SecurityContext with a Username-based
 *      Authentication whose authorities list carries ROLE_<role>. The
 *      principal name is the userId UUID so downstream code can fetch the
 *      user from UserRepository if needed.
 *   5. On missing header, malformed header, invalid token, or a failed
 *      revocation check, leave the SecurityContext empty. The downstream
 *      chain decides — controllers mapped to permitAll() continue; protected
 *      routes trigger 401.
 *
 * The filter does NOT throw on bad credentials. Throwing here would skip
 * the chain and surface 500 for unauthenticated traffic; we let auth
 * happen via the standard entry point.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final AccessTokenIssuer accessTokenIssuer;
    private final TokenBlacklistPort tokenBlacklistPort;

    public JwtAuthenticationFilter(
        AccessTokenIssuer accessTokenIssuer, TokenBlacklistPort tokenBlacklistPort
    ) {
        this.accessTokenIssuer = accessTokenIssuer;
        this.tokenBlacklistPort = tokenBlacklistPort;
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

        // A signature-valid token is not necessarily a live one (#88): it may
        // have been revoked by a logout, a refresh-reuse detection or a
        // password reset since it was issued.
        if (isRevoked(parsed.get())) {
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication = buildAuthentication(parsed.get());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    /**
     * Fail-closed: any inability to prove the token is still current — a
     * blacklisted jti, a stale tokenVersion, or an unreachable Redis — leaves
     * the request unauthenticated rather than trusting the signature alone.
     */
    private boolean isRevoked(ParsedAccessToken claims) {
        try {
            if (tokenBlacklistPort.isBlacklisted(claims.jti())) {
                return true;
            }
            OptionalLong currentVersion =
                tokenBlacklistPort.currentTokenVersion(claims.userId().toString());
            // Absent projection means this user never had anything revoked,
            // which is the normal state — not a reason to refuse.
            return currentVersion.isPresent() && claims.tokenVersion() < currentVersion.getAsLong();
        } catch (RuntimeException redisUnavailable) {
            log.warn(
                "revocation check failed; refusing token userId={} cause={}",
                claims.userId(), redisUnavailable.getMessage()
            );
            return true;
        }
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
