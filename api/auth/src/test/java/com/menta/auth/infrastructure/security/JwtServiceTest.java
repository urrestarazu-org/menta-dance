package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AccessTokenIssuer.ParsedAccessToken;
import com.menta.auth.application.port.out.IssuedAccessToken;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.shared.domain.vo.Email;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RED-GREEN discipline: this test references JwtService BEFORE 3.1 GREEN
 * provides the impl, so the file must not compile until JwtService.java
 * exists in the same package.
 *
 * Covers spec auth-login "Login emite par de tokens válidos" — verifying the
 * access token:
 *   - is a compact JWT string
 *   - signs with HS256 (header alg=HS256)
 *   - carries sub=userId, role=canonical, tokenVersion=N claims
 *   - exposes a jti that is a valid UUID distinct across consecutive issues
 *   - ttl() matches the configured duration (tests a stable 15-min default)
 *
 * Strict TDD: this test asserts REAL behavior against the parsed JWT — not
 * a mock of JwtService. The full HS256 round-trip via jjwt proves the token
 * is a verifiable JWT conforming to the spec.
 */
class JwtServiceTest {

    private static final String SECRET_BASE64 =
        // 64 random bytes (>= 256 bits) encoded as Base64 so we satisfy HS256 secret length.
        "bWljcm9zZXJ2aWNlLXNlY3JldC1mb3ItbWVudGEtZGFuY2UtYXV0aC1qd3QtaHMyNTY="
            + "LXByaXZhdGUta2V5LWFuYWx5c2VzLW9ubHktYjY0LWVuY29kaW5n";

    private JwtService newJwtService() {
        return new JwtService(SECRET_BASE64, Duration.ofMinutes(15));
    }

    /**
     * User's public 7-arg constructor pins tokenVersion=1; bumpTokenVersion() is
     * the only way fixtures can land with a higher version (PR1 helper pattern).
     */
    private User activeUser(UUID id, Role role, long tokenVersion) {
        User base = new User(
            UserId.of(id),
            Email.of("alice@example.com"),
            "$2a$10$hashplaceholder",
            role,
            UserStatus.ACTIVE,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        for (long i = 1; i < tokenVersion; i++) {
            base.bumpTokenVersion();
        }
        return base;
    }

    @Nested
    @DisplayName("Spec: Login emite par de tokens válidos — access claims")
    class AccessTokenClaims {

        @Test
        void issues_compact_jwt_string_with_hs256_alg() {
            AccessTokenIssuer issuer = newJwtService();
            User user = activeUser(UUID.randomUUID(), Role.STUDENT, 1L);

            IssuedAccessToken issued = issuer.issue(user);

            assertThat(issued.token())
                .as("token MUST be a compact JWT string (3 base64url segments)")
                .isNotNull()
                .contains(".");
            String[] segments = issued.token().split("\\.");
            assertThat(segments).hasSize(3);

            // Decode header without verification: must advertise HS256 algorithm.
            String headerJson = new String(Decoders.BASE64URL.decode(segments[0]));
            assertThat(headerJson).contains("\"alg\":\"HS256\"");
        }

        @Test
        void embeds_sub_role_tokenVersion_and_jti_claims_matching_user() {
            AccessTokenIssuer issuer = newJwtService();
            UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
            User user = activeUser(userId, Role.INSTRUCTOR, 7L);

            IssuedAccessToken issued = issuer.issue(user);

            // Parse with verification using the same secret: real GREEN proves
            // both signing AND claim projection work end-to-end.
            var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_BASE64)))
                .build()
                .parseSignedClaims(issued.token())
                .getPayload();

            assertThat(claims.getSubject()).isEqualTo(userId.toString());
            assertThat(claims.get("role", String.class)).isEqualTo("INSTRUCTOR");
            assertThat(claims.get("tokenVersion", Long.class)).isEqualTo(7L);
            assertThat(claims.getId())
                .as("jti claim MUST be set")
                .isNotNull();
            UUID.fromString(claims.getId()); // → throws if not a valid UUID
            // Expiration is in the future, matching ttl().
            assertThat(claims.getExpiration().toInstant())
                .isAfter(new Date().toInstant());
        }

        @Test
        void jti_is_unique_across_consecutive_issues_for_same_user() {
            AccessTokenIssuer issuer = newJwtService();
            User user = activeUser(UUID.randomUUID(), Role.STUDENT, 1L);

            IssuedAccessToken first = issuer.issue(user);
            IssuedAccessToken second = issuer.issue(user);

            assertThat(first.jti()).isNotEqualTo(second.jti());
        }
    }

    @Nested
    @DisplayName("Spec: TTL consistente")
    class TokenTtl {

        @Test
        void ttl_is_exactly_the_configured_duration() {
            JwtService issued = newJwtService();
            assertThat(issued.ttl()).isEqualTo(Duration.ofMinutes(15));
        }

        @Test
        void token_expiration_is_now_plus_ttl_within_a_slack_window() {
            AccessTokenIssuer issuer = newJwtService();
            User user = activeUser(UUID.randomUUID(), Role.STUDENT, 1L);

            long before = System.currentTimeMillis();
            IssuedAccessToken issued = issuer.issue(user);
            long after = System.currentTimeMillis();

            var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_BASE64)))
                .build()
                .parseSignedClaims(issued.token())
                .getPayload();

            // System.currentTimeMillis() and Instant.now() can drift slightly
            // across JVM clock sources; allow 2s slack on each side so the test
            // asserts ordering and bound, not exact monotonic alignment.
            long toleranceMs = 2_000;
            long expectedMin = before + Duration.ofMinutes(15).toMillis() - toleranceMs;
            long expectedMax = after + Duration.ofMinutes(15).toMillis() + toleranceMs;

            assertThat(claims.getExpiration().toInstant().toEpochMilli())
                .isBetween(expectedMin, expectedMax);
        }
    }

    @Nested
    @DisplayName("Spec: HS256 signing integrity")
    class SignAndVerify {

        @Test
        void signed_token_rejects_tamper_in_payload() {
            AccessTokenIssuer issuer = newJwtService();
            User user = activeUser(UUID.randomUUID(), Role.STUDENT, 1L);

            IssuedAccessToken good = issuer.issue(user);

            // Tamper with the payload middle segment: flip a few characters.
            String[] parts = good.token().split("\\.");
            String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2)
                + "AA" + "." + parts[2];

            assertThatThrownBy(() -> Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_BASE64)))
                .build()
                .parseSignedClaims(tampered))
                .as("Token MUST be HS256-signed so tampering fails verification")
                .isInstanceOfAny(
                    io.jsonwebtoken.security.SignatureException.class,
                    io.jsonwebtoken.JwtException.class);
        }
    }

    @Nested
    @DisplayName("Spec: Config-time guard")
    class ConfigGuards {

        @Test
        void rejects_short_secret_for_hs256() {
            // HS256 requires >= 256 bits (32 bytes). A 16-byte secret MUST
            // fail-fast at construction time, not at first issue call.
            String shortSecret = "too-short-secret";

            assertThatThrownBy(() -> new JwtService(shortSecret, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Spec: Parse and verify Bearer tokens (3.5 wiring) ")
    class ParseTokens {

        @Test
        void parses_a_freshly_issued_token_into_claims() {
            JwtService issuer = newJwtService();
            UUID userId = UUID.fromString("99999999-9999-9999-9999-999999999999");
            User user = activeUser(userId, Role.ADMIN, 4L);

            IssuedAccessToken issued = issuer.issue(user);
            Optional<ParsedAccessToken> parsed = issuer.parse(issued.token());

            assertThat(parsed).isPresent();
            ParsedAccessToken claims = parsed.get();
            assertThat(claims.userId()).isEqualTo(userId);
            assertThat(claims.role()).isEqualTo(Role.ADMIN);
            assertThat(claims.tokenVersion()).isEqualTo(4L);
            assertThat(claims.jti()).isNotEmpty();
        }

        @Test
        void rejects_token_signed_with_different_secret() {
            // Issue with one secret; parse with another — MUST fail closed.
            String otherSecret = "cGlwZS1zZWNyZXQtbnVuay1mb3ItdGVzdGluZy1zZWNyZXRzLW"
                + "+hYW5kLWJvbmFyZGVhZHMtbXVzdC1iZS0zMi1ieXRlcw==";
            JwtService a = newJwtService();
            JwtService b = new JwtService(otherSecret, Duration.ofMinutes(15));
            User user = activeUser(UUID.randomUUID(), Role.STUDENT, 1L);

            IssuedAccessToken issued = a.issue(user);
            Optional<ParsedAccessToken> parsed = b.parse(issued.token());

            assertThat(parsed).isEmpty();
        }

        @Test
        void rejects_expired_token() {
            // The TTL on the parser itself doesn't matter — we parse a
            // pre-crafted expired token (issued 2020, exp 2020).
            JwtService issuer = new JwtService(SECRET_BASE64, Duration.ofMinutes(15));

            Optional<ParsedAccessToken> parsed = issuer.parse(
                Jwts.builder()
                    .id(UUID.randomUUID().toString())
                    .subject(UUID.randomUUID().toString())
                    .issuedAt(Date.from(Instant.parse("2020-01-01T00:00:00Z")))
                    .expiration(Date.from(Instant.parse("2020-01-01T00:01:00Z")))
                    .claim("role", "STUDENT")
                    .claim("tokenVersion", 1L)
                    .signWith(
                        Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_BASE64)),
                        Jwts.SIG.HS256
                    ).compact()
            );

            assertThat(parsed).isEmpty();
        }

        @Test
        void rejects_garbage_string() {
            JwtService issuer = newJwtService();
            assertThat(issuer.parse("not.a.token")).isEmpty();
            assertThat(issuer.parse("")).isEmpty();
            assertThat(issuer.parse(null)).isEmpty();
        }
    }
}
