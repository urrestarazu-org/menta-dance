package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The login path tells the Auth API's failure answers apart (#479); the refresh path keeps
 * its own contract, which this class pins down as well.
 */
@WireMockTest
@DisplayName("AuthApiAdapter login failures")
class AuthApiAdapterLoginFailureTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final LoginCommand COMMAND = new LoginCommand("user@example.com", "ValidPass123!", "203.0.113.9");

    private AuthApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        AuthProperties properties = new AuthProperties();
        properties.setBaseUrl(wmRuntimeInfo.getHttpBaseUrl());
        properties.setTimeout(Duration.ofSeconds(5));
        adapter = new AuthApiAdapter(WebClient.builder().baseUrl(wmRuntimeInfo.getHttpBaseUrl()).build(), properties);
    }

    private static ResponseDefinitionBuilder problem(int status, String code) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"code\":\"" + code + "\",\"status\":" + status + "}");
    }

    @Test
    @DisplayName("423 USER_LOCKED becomes AccountLockedException, not a revoked refresh token")
    void lockedAccount() {
        stubFor(post(urlEqualTo(LOGIN)).willReturn(problem(423, "USER_LOCKED")));

        assertThatThrownBy(() -> adapter.login(COMMAND))
                .isInstanceOf(AuthApiClient.AccountLockedException.class)
                .isNotInstanceOf(AuthApiClient.RefreshTokenRevokedException.class);
    }

    @Test
    @DisplayName("429 carries the Retry-After seconds")
    void rateLimitedWithRetryAfter() {
        stubFor(post(urlEqualTo(LOGIN))
                .willReturn(problem(429, "LOGIN_RATE_LIMITED").withHeader("Retry-After", "120")));

        assertThatThrownBy(() -> adapter.login(COMMAND))
                .isInstanceOfSatisfying(AuthApiClient.RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(120)));
    }

    @Test
    @DisplayName("429 without Retry-After carries no delay")
    void rateLimitedWithoutRetryAfter() {
        stubFor(post(urlEqualTo(LOGIN)).willReturn(problem(429, "LOGIN_RATE_LIMITED")));

        assertThatThrownBy(() -> adapter.login(COMMAND))
                .isInstanceOfSatisfying(AuthApiClient.RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isNull());
    }

    @ParameterizedTest(name = "Retry-After \"{0}\" is tolerated")
    @ValueSource(strings = {"soon", "-5", "Wed, 21 Oct 2026 07:28:00 GMT", ""})
    @DisplayName("429 with an unusable Retry-After carries no delay")
    void rateLimitedWithGarbageRetryAfter(String garbage) {
        stubFor(post(urlEqualTo(LOGIN))
                .willReturn(problem(429, "LOGIN_RATE_LIMITED").withHeader("Retry-After", garbage)));

        assertThatThrownBy(() -> adapter.login(COMMAND))
                .isInstanceOfSatisfying(AuthApiClient.RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isNull());
    }

    @Test
    @DisplayName("403 ACCOUNT_NOT_ACTIVATED becomes AccountNotActivatedException")
    void accountNotActivated() {
        stubFor(post(urlEqualTo(LOGIN)).willReturn(problem(403, "ACCOUNT_NOT_ACTIVATED")));

        assertThatThrownBy(() -> adapter.login(COMMAND))
                .isInstanceOf(AuthApiClient.AccountNotActivatedException.class);
    }

    @Test
    @DisplayName("403 with another code is an unexpected answer, so unavailable")
    void forbiddenWithAnotherCode() {
        stubFor(post(urlEqualTo(LOGIN)).willReturn(problem(403, "SOMETHING_ELSE")));

        assertThatThrownBy(() -> adapter.login(COMMAND))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @ParameterizedTest(name = "status {0} is unavailable")
    @ValueSource(ints = {400, 404, 418, 500, 502})
    @DisplayName("any status the contract does not list is unavailable, never a server error")
    void unexpectedStatus(int status) {
        stubFor(post(urlEqualTo(LOGIN)).willReturn(aResponse().withStatus(status).withBody("boom")));

        assertThatThrownBy(() -> adapter.login(COMMAND))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("refresh keeps mapping 423 to RefreshTokenRevokedException")
    void refreshKeeps423() {
        stubFor(post(urlEqualTo(REFRESH)).willReturn(problem(423, "USER_LOCKED")));

        assertThatThrownBy(() -> adapter.refresh("refresh_token"))
                .isInstanceOf(AuthApiClient.RefreshTokenRevokedException.class);
    }
}
