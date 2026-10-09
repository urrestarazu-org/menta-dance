package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
