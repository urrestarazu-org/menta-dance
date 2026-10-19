package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.RequestPasswordResetCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Contract of the password recovery endpoints as seen through {@link AuthApiAdapter}.
 */
@WireMockTest
@DisplayName("AuthApiAdapter - password recovery")
class AuthApiAdapterPasswordResetTest {

    private static final String FORGOT = "/api/v1/auth/forgot-password";
    private static final String RESET = "/api/v1/auth/reset-password";
    private static final String EMAIL = "user@example.com";
    private static final String TOKEN = "reset-token-abc";
    private static final String PASSWORD = "Password1";
    private static final RequestPasswordResetCommand COMMAND = new RequestPasswordResetCommand(EMAIL, "203.0.113.9");

    private AuthApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        String baseUrl = wmRuntimeInfo.getHttpBaseUrl();
        AuthProperties properties = new AuthProperties();
        properties.setBaseUrl(baseUrl);
        properties.setTimeout(Duration.ofSeconds(5));
        adapter = new AuthApiAdapter(WebClient.builder().baseUrl(baseUrl).build(), properties);
    }

    private static void stub(String path, int status, String body) {
        stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status).withBody(body)));
    }

    @Test
    @DisplayName("forgot-password sends the email and forwards the client origin")
    void forgotSendsEmailAndOrigin() {
        stub(FORGOT, 202, "");

        adapter.requestPasswordReset(COMMAND);

        verify(postRequestedFor(urlEqualTo(FORGOT))
                .withHeader("X-Forwarded-For", equalTo("203.0.113.9"))
                .withRequestBody(matchingJsonPath("$.email", equalTo(EMAIL))));
    }

    @Test
    @DisplayName("forgot-password maps 400, 429 (with its wait) and any other status")
    void forgotMapsFailures() {
        stub(FORGOT, 400, "");
        assertThatThrownBy(() -> adapter.requestPasswordReset(COMMAND))
                .isInstanceOf(AuthApiClient.InvalidRequestException.class);

        stubFor(post(urlEqualTo(FORGOT)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "90")));
        assertThatThrownBy(() -> adapter.requestPasswordReset(COMMAND))
                .isInstanceOfSatisfying(AuthApiClient.RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(90)));

        stub(FORGOT, 503, "");
        assertThatThrownBy(() -> adapter.requestPasswordReset(COMMAND))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("reset-password sends the token and the new password in the JSON body")
    void resetSendsTokenAndPasswordInTheBody() {
        stub(RESET, 200, "");

        adapter.resetPassword(TOKEN, PASSWORD);

        verify(postRequestedFor(urlEqualTo(RESET))
                .withRequestBody(matchingJsonPath("$.token", equalTo(TOKEN)))
                .withRequestBody(matchingJsonPath("$.newPassword", equalTo(PASSWORD))));
    }

    @ParameterizedTest(name = "{0} {1} is an unusable token")
    @CsvSource({"404,PASSWORD_RESET_TOKEN_NOT_FOUND", "410,PASSWORD_RESET_TOKEN_EXPIRED",
        "410,PASSWORD_RESET_TOKEN_ALREADY_USED"})
    void resetMapsUnusableTokens(int status, String code) {
        stub(RESET, status, "{\"code\":\"" + code + "\"}");

        assertThatThrownBy(() -> adapter.resetPassword(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.InvalidResetTokenException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("reset-password tells a weak password from the current one, and from a malformed request")
    void resetMapsPasswordRejections() {
        stub(RESET, 400, "{\"code\":\"WEAK_PASSWORD\",\"detail\":\"x\"}");
        assertThatThrownBy(() -> adapter.resetPassword(TOKEN, PASSWORD))
                .isInstanceOfSatisfying(AuthApiClient.PasswordRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(AuthApiClient.PasswordRejectedException.Reason.WEAK));

        stub(RESET, 400, "{\"code\":\"SAME_PASSWORD\"}");
        assertThatThrownBy(() -> adapter.resetPassword(TOKEN, PASSWORD))
                .isInstanceOfSatisfying(AuthApiClient.PasswordRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(
                                AuthApiClient.PasswordRejectedException.Reason.SAME_AS_CURRENT));

        stub(RESET, 400, "{\"code\":\"INVALID_REQUEST\"}");
        assertThatThrownBy(() -> adapter.resetPassword(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.InvalidRequestException.class);

        stub(RESET, 400, "not json");
        assertThatThrownBy(() -> adapter.resetPassword(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.InvalidRequestException.class);

        stub(RESET, 400, "");
        assertThatThrownBy(() -> adapter.resetPassword(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.InvalidRequestException.class);
    }

    @Test
    @DisplayName("reset-password maps 429 and degrades unknown statuses and transport errors to unavailable")
    void resetMapsTheRest() {
        stubFor(post(urlEqualTo(RESET)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));
        assertThatThrownBy(() -> adapter.resetPassword(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.RateLimitedException.class);

        stub(RESET, 500, "");
        assertThatThrownBy(() -> adapter.resetPassword(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);

        stubFor(post(urlEqualTo(RESET)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.resetPassword(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN).hasMessageNotContaining(PASSWORD);
    }
}
