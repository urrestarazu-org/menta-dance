package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.RegisterCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Contract of the public account endpoints (register, activation, resend)
 * as seen through {@link AuthApiAdapter}.
 */
@WireMockTest
@DisplayName("AuthApiAdapter - account registration and activation")
class AuthApiAdapterAccountTest {

    private static final String CLIENT_ADDRESS = "203.0.113.9";
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String ACTIVATE = "/api/v1/auth/activate";
    private static final String TOKEN = "activation-token-abc";

    private AuthApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        String baseUrl = wmRuntimeInfo.getHttpBaseUrl();
        AuthProperties properties = new AuthProperties();
        properties.setBaseUrl(baseUrl);
        properties.setTimeout(Duration.ofSeconds(5));
        adapter = new AuthApiAdapter(WebClient.builder().baseUrl(baseUrl).build(), properties);
    }

    private static void stubStatus(String path, int status) {
        stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status)));
    }

    private static RegisterCommand registration() {
        return RegisterCommand.student("ana@example.com", "Secreta123", CLIENT_ADDRESS);
    }

    @Test
    @DisplayName("register sends email, password and the STUDENT role, forwarding the client origin")
    void registerSendsStudentRole() {
        stubStatus(REGISTER, 202);

        adapter.register(registration());

        verify(postRequestedFor(urlEqualTo(REGISTER))
                .withHeader("X-Forwarded-For", equalTo(CLIENT_ADDRESS))
                .withRequestBody(matchingJsonPath("$.email", equalTo("ana@example.com")))
                .withRequestBody(matchingJsonPath("$.password", equalTo("Secreta123")))
                .withRequestBody(matchingJsonPath("$.role", equalTo("STUDENT"))));
    }

    @Test
    @DisplayName("register maps 400 to InvalidRequestException")
    void registerMaps400() {
        stubStatus(REGISTER, 400);

        assertThatThrownBy(() -> adapter.register(registration()))
                .isInstanceOf(AuthApiClient.InvalidRequestException.class);
    }

    @Test
    @DisplayName("register maps 429 to RateLimitedException carrying Retry-After")
    void registerMaps429() {
        stubFor(post(urlEqualTo(REGISTER)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));

        assertThatThrownBy(() -> adapter.register(registration()))
                .isInstanceOfSatisfying(AuthApiClient.RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    @DisplayName("register maps a 429 without a usable Retry-After to RateLimitedException with no delay")
    void registerMaps429WithoutRetryAfter() {
        stubFor(post(urlEqualTo(REGISTER)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "soon")));

        assertThatThrownBy(() -> adapter.register(registration()))
                .isInstanceOfSatisfying(AuthApiClient.RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isNull());
    }

    @Test
    @DisplayName("register maps 503 to ServiceUnavailableException")
    void registerMaps503() {
        stubStatus(REGISTER, 503);

        assertThatThrownBy(() -> adapter.register(registration()))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("validateActivationToken calls GET with the token in the path and accepts 204")
    void validateAccepts204() {
        stubFor(get(urlEqualTo(ACTIVATE + "/" + TOKEN)).willReturn(aResponse().withStatus(204)));

        assertThatCode(() -> adapter.validateActivationToken(TOKEN)).doesNotThrowAnyException();

        verify(getRequestedFor(urlEqualTo(ACTIVATE + "/" + TOKEN)));
    }

    @Test
    @DisplayName("validateActivationToken maps 400 to InvalidActivationTokenException without echoing the token")
    void validateMaps400() {
        stubFor(get(urlEqualTo(ACTIVATE + "/" + TOKEN)).willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> adapter.validateActivationToken(TOKEN))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("validateActivationToken maps 503 to ServiceUnavailableException")
    void validateMaps503() {
        stubFor(get(urlEqualTo(ACTIVATE + "/" + TOKEN)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> adapter.validateActivationToken(TOKEN))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("activate posts the token in the JSON body, never in the URL, and accepts 204")
    void activateAccepts204() {
        stubStatus(ACTIVATE, 204);

        adapter.activate(TOKEN);

        verify(postRequestedFor(urlEqualTo(ACTIVATE))
                .withRequestBody(matchingJsonPath("$.token", equalTo(TOKEN))));
    }

    @Test
    @DisplayName("activate maps 400 to InvalidActivationTokenException without echoing the token")
    void activateMaps400() {
        stubStatus(ACTIVATE, 400);

        assertThatThrownBy(() -> adapter.activate(TOKEN))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("activate maps 429 to RateLimitedException carrying Retry-After")
    void activateMaps429() {
        stubFor(post(urlEqualTo(ACTIVATE)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "15")));

        assertThatThrownBy(() -> adapter.activate(TOKEN))
                .isInstanceOfSatisfying(AuthApiClient.RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(15)));
    }

    @Test
    @DisplayName("activate maps 503 to ServiceUnavailableException")
    void activateMaps503() {
        stubStatus(ACTIVATE, 503);

        assertThatThrownBy(() -> adapter.activate(TOKEN))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a transport failure while validating reports unavailable without the token in the message")
    void transportFailureDoesNotLeakTheToken() {
        stubFor(get(urlEqualTo(ACTIVATE + "/" + TOKEN))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter.validateActivationToken(TOKEN))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
    }
}
