package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AccessTokenIssuer.ParsedAccessToken;
import com.menta.auth.domain.model.Role;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;
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
    @Mock private FilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(accessTokenIssuer);
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
}
