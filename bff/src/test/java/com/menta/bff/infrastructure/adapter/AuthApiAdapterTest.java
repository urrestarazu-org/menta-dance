package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.dto.TokenPairResponse;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("AuthApiAdapter")
class AuthApiAdapterTest {

    private static final String CLIENT_ADDRESS = "203.0.113.9";
    private static final String ACCESS_TOKEN = "access.jwt.value";
    private static final String REFRESH_TOKEN = "refresh_token_123";

    private AuthApiAdapter authApiAdapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        String baseUrl = wmRuntimeInfo.getHttpBaseUrl();

        AuthProperties authProperties = new AuthProperties();
        authProperties.setBaseUrl(baseUrl);
        authProperties.setTimeout(Duration.ofSeconds(5));

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

        objectMapper = new ObjectMapper();
        authApiAdapter = new AuthApiAdapter(webClient, authProperties);
    }

    @Test
    @DisplayName("should login successfully and return token pair")
    void shouldLoginSuccessfullyAndReturnTokenPair() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);

        Map<String, Object> responseBody = Map.of(
                "access_token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                "token_type", "Bearer",
                "expires_in", 900
        );

        stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .withRequestBody(matchingJsonPath("$.email", equalTo("user@example.com")))
                .withRequestBody(matchingJsonPath("$.password", equalTo("ValidPass123!")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh_abc123")
                        .withBody(toJson(responseBody))));

        // When
        TokenPairResponse result = authApiAdapter.login(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...");
        assertThat(result.refreshToken()).isEqualTo("refresh_abc123");
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    @DisplayName("should throw AuthenticationException when login returns 401")
    void shouldThrowAuthenticationExceptionWhenLoginReturns401() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "WrongPassword", CLIENT_ADDRESS);

        stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Invalid credentials\"}")));

        // When / Then
        assertThatThrownBy(() -> authApiAdapter.login(command))
                .isInstanceOf(AuthApiClient.AuthenticationException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException when login returns 503")
    void shouldThrowServiceUnavailableExceptionWhenLoginReturns503() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);

        stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Service temporarily unavailable\"}")));

        // When / Then
        assertThatThrownBy(() -> authApiAdapter.login(command))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class)
                .hasMessageContaining("Auth API unavailable");
    }

    @Test
    @DisplayName("should refresh token successfully")
    void shouldRefreshTokenSuccessfully() {
        // Given
        String refreshToken = "refresh_token_123";

        Map<String, Object> responseBody = Map.of(
                "access_token", "new_access_token",
                "token_type", "Bearer",
                "expires_in", 900
        );

        stubFor(post(urlEqualTo("/api/v1/auth/refresh"))
                .withHeader("X-Refresh-Token", equalTo(refreshToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "new_refresh_token")
                        .withBody(toJson(responseBody))));

        // When
        TokenPairResponse result = authApiAdapter.refresh(refreshToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("new_access_token");
        assertThat(result.refreshToken()).isEqualTo("new_refresh_token");
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    @DisplayName("should throw AuthenticationException when refresh returns 401")
    void shouldThrowAuthenticationExceptionWhenRefreshReturns401() {
        // Given
        String refreshToken = "invalid_refresh_token";

        stubFor(post(urlEqualTo("/api/v1/auth/refresh"))
                .withHeader("X-Refresh-Token", equalTo(refreshToken))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Invalid refresh token\"}")));

        // When / Then
        assertThatThrownBy(() -> authApiAdapter.refresh(refreshToken))
                .isInstanceOf(AuthApiClient.AuthenticationException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("should throw RefreshTokenRevokedException when refresh returns 423")
    void shouldThrowRefreshTokenRevokedExceptionWhenRefreshReturns423() {
        // Given
        String refreshToken = "revoked_refresh_token";

        stubFor(post(urlEqualTo("/api/v1/auth/refresh"))
                .withHeader("X-Refresh-Token", equalTo(refreshToken))
                .willReturn(aResponse()
                        .withStatus(423)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Token family revoked\"}")));

        // When / Then
        assertThatThrownBy(() -> authApiAdapter.refresh(refreshToken))
                .isInstanceOf(AuthApiClient.RefreshTokenRevokedException.class)
                .hasMessageContaining("Token family revoked");
    }

    @Test
    @DisplayName("should logout successfully sending both the bearer access token and the refresh token")
    void shouldLogoutSuccessfullyWithBothTokens() {
        // Given
        stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .withHeader("X-Refresh-Token", equalTo(REFRESH_TOKEN))
                .willReturn(aResponse()
                        .withStatus(204)));

        // When / Then - Should not throw
        authApiAdapter.logout(ACCESS_TOKEN, REFRESH_TOKEN);

        // Verify the request carried both credentials with their exact values
        verify(postRequestedFor(urlEqualTo("/api/v1/auth/logout"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .withHeader("X-Refresh-Token", equalTo(REFRESH_TOKEN)));
    }

    @Test
    @DisplayName("should handle logout idempotently when returns 404 (already revoked)")
    void shouldHandleLogoutIdempotentlyWhenReturns404() {
        // Given
        stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Token not found\"}")));

        // When / Then - Should not throw (idempotent logout)
        authApiAdapter.logout(ACCESS_TOKEN, REFRESH_TOKEN);
    }

    @Test
    @DisplayName("should throw AuthenticationException when logout returns 401")
    void shouldThrowAuthenticationExceptionWhenLogoutReturns401() {
        // Given
        stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Unauthorized\"}")));

        // When / Then - the refresh token was NOT revoked, so this must not look like success
        assertThatThrownBy(() -> authApiAdapter.logout(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthApiClient.AuthenticationException.class);
    }

    @Test
    @DisplayName("should throw AuthenticationException when logout returns 403")
    void shouldThrowAuthenticationExceptionWhenLogoutReturns403() {
        // Given
        stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                .willReturn(aResponse().withStatus(403)));

        // When / Then
        assertThatThrownBy(() -> authApiAdapter.logout(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthApiClient.AuthenticationException.class);
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException when logout returns 503")
    void shouldThrowServiceUnavailableExceptionWhenLogoutReturns503() {
        // Given
        stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                .willReturn(aResponse()
                        .withStatus(503)));

        // When / Then
        assertThatThrownBy(() -> authApiAdapter.logout(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException when logout returns 500")
    void shouldThrowServiceUnavailableExceptionWhenLogoutReturns500() {
        // Given
        stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                .willReturn(aResponse().withStatus(500)));

        // When / Then
        assertThatThrownBy(() -> authApiAdapter.logout(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("should not silently ignore an unexpected logout status")
    void shouldNotSilentlyIgnoreAnUnexpectedLogoutStatus() {
        // Given
        stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                .willReturn(aResponse()
                        .withStatus(418)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"I'm a teapot\"}")));

        // When / Then - an unexpected status degrades to "unavailable", never to success
        assertThatThrownBy(() -> authApiAdapter.logout(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException when refresh returns 503")
    void shouldThrowServiceUnavailableExceptionWhenRefreshReturns503() {
        // Given
        String refreshToken = "refresh_token_123";

        stubFor(post(urlEqualTo("/api/v1/auth/refresh"))
                .withHeader("X-Refresh-Token", equalTo(refreshToken))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Service temporarily unavailable\"}")));

        // When / Then
        assertThatThrownBy(() -> authApiAdapter.refresh(refreshToken))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class)
                .hasMessageContaining("Auth API unavailable");
    }

    @Test
    @DisplayName("should throw RuntimeException when login succeeds without a refresh token header")
    void shouldThrowRuntimeExceptionWhenLoginSucceedsWithoutRefreshTokenHeader() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);

        stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(Map.of(
                                "access_token", "compact.jwt.value",
                                "token_type", "Bearer",
                                "expires_in", 900
                        )))));

        // When / Then
        assertThatThrownBy(() -> authApiAdapter.login(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("did not return");
    }

    @Test
    @DisplayName("should throw RuntimeException when login succeeds with an empty response body")
    void shouldThrowRuntimeExceptionWhenLoginSucceedsWithAnEmptyResponseBody() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);

        stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Refresh-Token", "refresh_abc123")));

        // When / Then
        assertThatThrownBy(() -> authApiAdapter.login(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty response body");
    }

    @Test
    @DisplayName("should default expires_in to 900 seconds when the field is missing")
    void shouldDefaultExpiresInWhenFieldIsMissing() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);

        stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh_abc123")
                        .withBody(toJson(Map.of(
                                "access_token", "compact.jwt.value",
                                "token_type", "Bearer"
                        )))));

        // When
        TokenPairResponse result = authApiAdapter.login(command);

        // Then
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    @DisplayName("should parse expires_in when returned as an ISO-8601 duration string")
    void shouldParseExpiresInWhenReturnedAsIsoDurationString() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);

        stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh_abc123")
                        .withBody(toJson(Map.of(
                                "access_token", "compact.jwt.value",
                                "token_type", "Bearer",
                                "expires_in", "PT15M"
                        )))));

        // When
        TokenPairResponse result = authApiAdapter.login(command);

        // Then
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    @DisplayName("should default expires_in to 900 seconds when the duration string is invalid")
    void shouldDefaultExpiresInWhenDurationStringIsInvalid() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);

        stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh_abc123")
                        .withBody(toJson(Map.of(
                                "access_token", "compact.jwt.value",
                                "token_type", "Bearer",
                                "expires_in", "not-a-duration"
                        )))));

        // When
        TokenPairResponse result = authApiAdapter.login(command);

        // Then
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    @DisplayName("should default expires_in to 900 seconds when the field has an unexpected type")
    void shouldDefaultExpiresInWhenFieldHasAnUnexpectedType() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);

        stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh_abc123")
                        .withBody(toJson(Map.of(
                                "access_token", "compact.jwt.value",
                                "token_type", "Bearer",
                                "expires_in", true
                        )))));

        // When
        TokenPairResponse result = authApiAdapter.login(command);

        // Then
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    @DisplayName("should never log the access token or the refresh token when logout fails")
    void shouldNeverLogTokensWhenLogoutFails() {
        // Given
        Logger adapterLogger = (Logger) LoggerFactory.getLogger(AuthApiAdapter.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        adapterLogger.addAppender(logs);
        stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                .willReturn(aResponse().withStatus(401)));

        try {
            // When
            assertThatThrownBy(() -> authApiAdapter.logout(ACCESS_TOKEN, REFRESH_TOKEN))
                    .isInstanceOf(AuthApiClient.AuthenticationException.class)
                    .hasMessageNotContaining(ACCESS_TOKEN)
                    .hasMessageNotContaining(REFRESH_TOKEN);

            // Then
            assertThat(logs.list).isNotEmpty();
            assertThat(logs.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(ACCESS_TOKEN) || message.contains(REFRESH_TOKEN));
        } finally {
            adapterLogger.detachAppender(logs);
        }
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException when logout cannot reach the Auth API")
    void shouldThrowServiceUnavailableExceptionWhenLogoutCannotReachAuthApi() throws IOException {
        // Given - an address with nobody listening, to force a connection failure
        int unusedPort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            unusedPort = serverSocket.getLocalPort();
        }
        String unreachableUrl = "http://localhost:" + unusedPort;

        AuthProperties unreachableProperties = new AuthProperties();
        unreachableProperties.setBaseUrl(unreachableUrl);
        unreachableProperties.setTimeout(Duration.ofSeconds(2));

        WebClient unreachableWebClient = WebClient.builder().baseUrl(unreachableUrl).build();
        AuthApiAdapter unreachableAdapter = new AuthApiAdapter(unreachableWebClient, unreachableProperties);

        // When / Then
        assertThatThrownBy(() -> unreachableAdapter.logout(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    // -- ADR-0035: trusted client origin propagation (BFF → API hop) --------

    @Test
    @DisplayName("should forward the canonical client origin as a single-value X-Forwarded-For")
    void shouldForwardCanonicalClientOrigin() {
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);
        stubLoginSuccess();

        authApiAdapter.login(command);

        verify(postRequestedFor(urlEqualTo("/api/v1/auth/login"))
                .withHeader("X-Forwarded-For", equalTo(CLIENT_ADDRESS)));
    }

    @Test
    @DisplayName("should never relay a proxy chain, only the resolved address")
    void shouldNeverRelayAProxyChain() {
        // The Auth API reads the LAST element of X-Forwarded-For. Relaying a
        // chain would let a caller append entries and pick which address gets
        // fingerprinted; one resolved value removes that choice.
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);
        stubLoginSuccess();

        authApiAdapter.login(command);

        verify(postRequestedFor(urlEqualTo("/api/v1/auth/login"))
                .withHeader("X-Forwarded-For", notMatching(".*,.*")));
    }

    @Test
    @DisplayName("should omit the header when no origin could be established")
    void shouldOmitTheHeaderWhenOriginIsUnknown() {
        // Omitted, not blank: the API then falls back to the peer address it
        // observes, which is the pre-ADR-0035 behaviour and never less safe.
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", null);
        stubLoginSuccess();

        authApiAdapter.login(command);

        verify(postRequestedFor(urlEqualTo("/api/v1/auth/login"))
                .withoutHeader("X-Forwarded-For"));
    }

    private void stubLoginSuccess() {
        stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh_token_123")
                        .withBody(toJson(Map.of(
                                "access_token", "compact.jwt.value",
                                "token_type", "Bearer",
                                "expires_in", 900
                        )))));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
