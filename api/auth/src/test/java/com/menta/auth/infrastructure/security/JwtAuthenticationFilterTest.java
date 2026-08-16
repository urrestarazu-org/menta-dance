package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AccessTokenIssuer.ParsedAccessToken;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.model.Role;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * RED-GREEN discipline: this test references JwtAuthenticationFilter BEFORE
 * 3.5 GREEN provides the impl, so it must not compile until the filter exists.
 *
 * Strict TDD: every assertion exercises the filter's behaviour through the
 * SecurityContext (a real side-effect, observable downstream). Returning
 * Optional.empty() from AccessTokenIssuer.parse leaves the SecurityContext
 * empty and the chain is allowed to handle the unauthenticated request.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private AccessTokenIssuer accessTokenIssuer;
    @Mock private TokenBlacklistPort tokenBlacklistPort;
    @Mock private FilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(accessTokenIssuer, tokenBlacklistPort);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Spec: Bearer token authentication")
    class BearerToken {

        @Test
        void sets_authentication_when_parse_returns_claims() throws Exception {
            UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            ParsedAccessToken parsed = new ParsedAccessToken(
                userId, Role.INSTRUCTOR, 5L, "jti-1"
            );
            when(accessTokenIssuer.parse("good.token.string")).thenReturn(Optional.of(parsed));
            when(tokenBlacklistPort.isBlacklisted("jti-1")).thenReturn(false);
            when(tokenBlacklistPort.currentTokenVersion(userId.toString()))
                .thenReturn(OptionalLong.empty());

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer good.token.string");
            HttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.isAuthenticated()).isTrue();
            assertThat(auth.getName()).isEqualTo(userId.toString());
            assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_INSTRUCTOR");
        }

        @Test
        void does_not_set_authentication_when_header_absent() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            HttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(accessTokenIssuer, never()).parse(any());
            verify(chain, times(1)).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void does_not_set_authentication_when_header_is_not_bearer() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
            HttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(accessTokenIssuer, never()).parse(any());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void does_not_set_authentication_when_parse_returns_empty() throws Exception {
            when(accessTokenIssuer.parse("expired.token")).thenReturn(Optional.empty());

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer expired.token");
            HttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("Spec: revocation enforcement (#88)")
    class RevocationEnforcement {

        private static final UUID USER_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        private void givenParsedToken(long tokenVersion) {
            ParsedAccessToken parsed =
                new ParsedAccessToken(USER_ID, Role.STUDENT, tokenVersion, "jti-9");
            when(accessTokenIssuer.parse("valid.token")).thenReturn(Optional.of(parsed));
        }

        private MockHttpServletRequest bearerRequest() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer valid.token");
            return request;
        }

        @Test
        void authenticates_a_valid_token_whose_user_never_revoked_anything() throws Exception {
            givenParsedToken(5L);
            when(tokenBlacklistPort.isBlacklisted("jti-9")).thenReturn(false);
            when(tokenBlacklistPort.currentTokenVersion(USER_ID.toString()))
                .thenReturn(OptionalLong.empty());

            filter.doFilter(bearerRequest(), new MockHttpServletResponse(), chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }

        @Test
        void authenticates_when_the_token_version_matches_the_projection() throws Exception {
            givenParsedToken(5L);
            when(tokenBlacklistPort.isBlacklisted("jti-9")).thenReturn(false);
            when(tokenBlacklistPort.currentTokenVersion(USER_ID.toString()))
                .thenReturn(OptionalLong.of(5L));

            filter.doFilter(bearerRequest(), new MockHttpServletResponse(), chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }

        @Test
        void refuses_a_blacklisted_jti() throws Exception {
            givenParsedToken(5L);
            when(tokenBlacklistPort.isBlacklisted("jti-9")).thenReturn(true);

            filter.doFilter(bearerRequest(), new MockHttpServletResponse(), chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void refuses_a_token_whose_version_is_older_than_the_projection() throws Exception {
            // The core of #88: a logout, refresh-reuse detection or password
            // reset bumped the version, so this still-unexpired token is dead.
            givenParsedToken(4L);
            when(tokenBlacklistPort.isBlacklisted("jti-9")).thenReturn(false);
            when(tokenBlacklistPort.currentTokenVersion(USER_ID.toString()))
                .thenReturn(OptionalLong.of(5L));

            filter.doFilter(bearerRequest(), new MockHttpServletResponse(), chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void does_not_refuse_a_token_newer_than_the_projection() throws Exception {
            // Can happen briefly while the reconciler catches up. A newer token
            // was issued after the revocation, so it is legitimately current.
            givenParsedToken(6L);
            when(tokenBlacklistPort.isBlacklisted("jti-9")).thenReturn(false);
            when(tokenBlacklistPort.currentTokenVersion(USER_ID.toString()))
                .thenReturn(OptionalLong.of(5L));

            filter.doFilter(bearerRequest(), new MockHttpServletResponse(), chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }

        @Test
        void refuses_when_redis_is_unreachable_on_the_blacklist_read() throws Exception {
            // Fail-closed: unable to prove the token is live means refuse.
            givenParsedToken(5L);
            when(tokenBlacklistPort.isBlacklisted("jti-9"))
                .thenThrow(new RuntimeException("connection refused"));

            filter.doFilter(bearerRequest(), new MockHttpServletResponse(), chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void refuses_when_redis_is_unreachable_on_the_version_read() throws Exception {
            givenParsedToken(5L);
            when(tokenBlacklistPort.isBlacklisted("jti-9")).thenReturn(false);
            when(tokenBlacklistPort.currentTokenVersion(USER_ID.toString()))
                .thenThrow(new RuntimeException("connection refused"));

            filter.doFilter(bearerRequest(), new MockHttpServletResponse(), chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void always_continues_the_chain_even_when_refusing() throws Exception {
            // The filter never short-circuits: the entry point renders 401.
            givenParsedToken(4L);
            when(tokenBlacklistPort.isBlacklisted("jti-9")).thenReturn(false);
            when(tokenBlacklistPort.currentTokenVersion(USER_ID.toString()))
                .thenReturn(OptionalLong.of(5L));
            MockHttpServletRequest request = bearerRequest();
            HttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
        }

        @Test
        void does_not_touch_redis_when_no_credentials_are_present() throws Exception {
            // An anonymous request must not cost a Redis round-trip.
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

            verify(tokenBlacklistPort, never()).isBlacklisted(any());
            verify(tokenBlacklistPort, never()).currentTokenVersion(any());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
