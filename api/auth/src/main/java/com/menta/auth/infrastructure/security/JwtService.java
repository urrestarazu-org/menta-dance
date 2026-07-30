package com.menta.auth.infrastructure.security;

import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.IssuedAccessToken;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * HS256 access-token issuer (ADR-0025).
 *
 * Produces compact JWTs with claims:
 *   - sub        : userId UUID (string per JWT convention)
 *   - role       : {@link com.menta.auth.domain.model.Role} name as canonical
 *                  English identifier (STUDENT / INSTRUCTOR / ADMIN).
 *   - tokenVersion: numeric snapshot of the user tokenVersion at issue time;
 *                  verification fails when the user tokenVersion advances.
 *   - jti (id)   : UUID v4 — also serves as outbox aggregate_id for the
 *                  AuthUserLoggedIn event projected by the reconciler.
 *   - iat / exp  : standard JWT timestamps; exp = now + ttl.
 *
 * Secret MUST be at least 32 bytes (HS256 requires 256-bit key material);
 * construction-time validation prevents weak secrets reaching the signing
 * path.
 */
public class JwtService implements AccessTokenIssuer {

    /**
     * HS256 minimum key length per RFC 7518 §3.2: 256 bits = 32 bytes.
     */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final Duration ttl;

    public JwtService(String base64Secret, Duration ttl) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalArgumentException("base64Secret must not be null or blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be a positive duration");
        }
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(base64Secret);
        } catch (RuntimeException e) {
            // jjwt's DecodingException extends RuntimeException directly. Translate
            // any decode failure to IllegalArgumentException at our boundary so
            // misconfigured secrets surface as a single, predictable error type.
            throw new IllegalArgumentException("base64Secret is not valid Base64", e);
        }
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                "HS256 secret must be at least " + MIN_SECRET_BYTES
                    + " bytes (256 bits); got " + decoded.length + " bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(decoded);
        this.ttl = ttl;
    }

    @Override
    public IssuedAccessToken issue(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        String jti = UUID.randomUUID().toString();

        String compact = Jwts.builder()
            .id(jti)
            .subject(user.getId().getValue().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claim("role", user.getRole().name())
            .claim("tokenVersion", user.getTokenVersion())
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();

        return new IssuedAccessToken(compact, jti, ttl);
    }

    @Override
    public Duration ttl() {
        return ttl;
    }

    @Override
    public Optional<ParsedAccessToken> parse(String compact) {
        if (compact == null || compact.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(compact)
                .getPayload();
            String subject = claims.getSubject();
            if (subject == null) {
                return Optional.empty();
            }
            UUID userId;
            try {
                userId = UUID.fromString(subject);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            String roleName = claims.get("role", String.class);
            if (roleName == null) {
                return Optional.empty();
            }
            Role role;
            try {
                role = Role.valueOf(roleName);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            Long tokenVersion = claims.get("tokenVersion", Long.class);
            if (tokenVersion == null) {
                return Optional.empty();
            }
            String jti = claims.getId();
            if (jti == null) {
                return Optional.empty();
            }
            return Optional.of(new ParsedAccessToken(userId, role, tokenVersion, jti));
        } catch (ExpiredJwtException e) {
            // Expired token: explicit failure so the filter rejects the request.
            return Optional.empty();
        } catch (JwtException e) {
            // Malformed, unsigned, alg mismatch, etc.
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
