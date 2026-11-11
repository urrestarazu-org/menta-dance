package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
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
import com.menta.bff.application.dto.ActivationRequirement;
import com.menta.bff.application.dto.RegisterCommand;
import com.menta.bff.application.dto.ResendActivationCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.time.Duration;
import java.time.LocalDate;
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
    private static final String RESEND = "/api/v1/auth/resend-activation";
    private static final String TOKEN = "activation-token-abc";
    private static final String PASSWORD = "Secreta123";

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

    private static void stubProblem(String path, String code) {
        stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":400,\"code\":\"" + code + "\",\"detail\":\"x\"}")));
    }

    private static RegisterCommand registration() {
        return RegisterCommand.student("ana@example.com", "Secreta123", "Ana", "Prueba", "+5491100000000", null,
                CLIENT_ADDRESS);
    }

    @Test
    @DisplayName("register sends email, password, personal data and the STUDENT role, forwarding the client origin")
    void registerSendsStudentRole() {
        stubStatus(REGISTER, 202);

        adapter.register(registration());

        verify(postRequestedFor(urlEqualTo(REGISTER))
                .withHeader("X-Forwarded-For", equalTo(CLIENT_ADDRESS))
                .withRequestBody(matchingJsonPath("$.email", equalTo("ana@example.com")))
                .withRequestBody(matchingJsonPath("$.password", equalTo("Secreta123")))
                .withRequestBody(matchingJsonPath("$.firstName", equalTo("Ana")))
                .withRequestBody(matchingJsonPath("$.lastName", equalTo("Prueba")))
                .withRequestBody(matchingJsonPath("$.phone", equalTo("+5491100000000")))
                .withRequestBody(matchingJsonPath("$.role", equalTo("STUDENT"))));
    }

    @Test
    @DisplayName("register omits the birth date from the body when the person did not give one")
    void registerOmitsAbsentBirthDate() {
        stubStatus(REGISTER, 202);

        adapter.register(registration());

        verify(postRequestedFor(urlEqualTo(REGISTER))
                .withRequestBody(notMatching(".*birthDate.*")));
    }

    @Test
    @DisplayName("register sends the birth date as an ISO yyyy-MM-dd string when present")
    void registerSendsBirthDateAsIso() {
        stubStatus(REGISTER, 202);
        RegisterCommand command = RegisterCommand.student("ana@example.com", "Secreta123", "Ana", "Prueba",
                "+5491100000000", LocalDate.of(1990, 5, 17), CLIENT_ADDRESS);

        adapter.register(command);

        verify(postRequestedFor(urlEqualTo(REGISTER))
                .withRequestBody(matchingJsonPath("$.birthDate", equalTo("1990-05-17"))));
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
    @DisplayName("validateActivationToken calls GET with the token in the path; 204 means no password is needed")
    void validateAccepts204() {
        stubFor(get(urlEqualTo(ACTIVATE + "/" + TOKEN)).willReturn(aResponse().withStatus(204)));

        assertThat(adapter.validateActivationToken(TOKEN)).isEqualTo(ActivationRequirement.NONE);

        verify(getRequestedFor(urlEqualTo(ACTIVATE + "/" + TOKEN)));
    }

    @Test
    @DisplayName("validateActivationToken reads 200 {passwordRequired: true} as a password the holder must choose")
    void validateAccepts200WithPasswordRequired() {
        stubFor(get(urlEqualTo(ACTIVATE + "/" + TOKEN)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"passwordRequired\": true}")));

        assertThat(adapter.validateActivationToken(TOKEN)).isEqualTo(ActivationRequirement.PASSWORD);
    }

    @Test
    @DisplayName("validateActivationToken treats a 200 that does not say passwordRequired as an unusable answer")
    void validateRejects200WithoutTheFlag() {
        stubFor(get(urlEqualTo(ACTIVATE + "/" + TOKEN)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"passwordRequired\": false}")));

        assertThatThrownBy(() -> adapter.validateActivationToken(TOKEN))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("validateActivationToken never reads a password verdict into a token verdict")
    void validateIgnoresPasswordCodes() {
        stubFor(get(urlEqualTo(ACTIVATE + "/" + TOKEN)).willReturn(aResponse().withStatus(400)
                .withBody("{\"code\":\"WEAK_PASSWORD\"}")));

        assertThatThrownBy(() -> adapter.validateActivationToken(TOKEN))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class);
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
    @DisplayName("activate with a password sends it in the JSON body next to the token and accepts 204")
    void activateWithPasswordSendsItInTheBody() {
        stubStatus(ACTIVATE, 204);

        adapter.activate(TOKEN, PASSWORD);

        verify(postRequestedFor(urlEqualTo(ACTIVATE))
                .withRequestBody(matchingJsonPath("$.token", equalTo(TOKEN)))
                .withRequestBody(matchingJsonPath("$.password", equalTo(PASSWORD))));
    }

    @Test
    @DisplayName("activate without a password sends no password field")
    void activateWithoutPasswordSendsNone() {
        stubStatus(ACTIVATE, 204);

        adapter.activate(TOKEN);

        verify(postRequestedFor(urlEqualTo(ACTIVATE)).withRequestBody(notMatching(".*password.*")));
    }

    @Test
    @DisplayName("activate maps 400 PASSWORD_REQUIRED to PasswordRequiredException")
    void activateMapsPasswordRequired() {
        stubProblem(ACTIVATE, "PASSWORD_REQUIRED");

        assertThatThrownBy(() -> adapter.activate(TOKEN))
                .isInstanceOf(AuthApiClient.PasswordRequiredException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("activate maps 400 WEAK_PASSWORD to a WEAK PasswordRejectedException without echoing secrets")
    void activateMapsWeakPassword() {
        stubProblem(ACTIVATE, "WEAK_PASSWORD");

        assertThatThrownBy(() -> adapter.activate(TOKEN, PASSWORD))
                .isInstanceOfSatisfying(AuthApiClient.PasswordRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(AuthApiClient.PasswordRejectedException.Reason.WEAK))
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(PASSWORD);
    }

    @Test
    @DisplayName("activate maps a 400 with the token verdict or an unknown code to InvalidActivationTokenException")
    void activateMapsOtherProblemsToAnInvalidToken() {
        stubProblem(ACTIVATE, "ACTIVATION_TOKEN_INVALID");
        assertThatThrownBy(() -> adapter.activate(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class);

        stubProblem(ACTIVATE, "SOMETHING_ELSE");
        assertThatThrownBy(() -> adapter.activate(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class);
    }

    @Test
    @DisplayName("activate with a password maps 429 and 503 like the plain activation")
    void activateWithPasswordMapsAvailabilityFailures() {
        stubFor(post(urlEqualTo(ACTIVATE)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "15")));
        assertThatThrownBy(() -> adapter.activate(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.RateLimitedException.class);

        stubStatus(ACTIVATE, 503);
        assertThatThrownBy(() -> adapter.activate(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a transport failure while activating reports unavailable without the password in the message")
    void activateTransportFailureDoesNotLeakThePassword() {
        stubFor(post(urlEqualTo(ACTIVATE)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter.activate(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(PASSWORD)
                .hasMessageNotContaining(TOKEN);
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

    private static ResendActivationCommand resend() {
        return new ResendActivationCommand("user@example.com", CLIENT_ADDRESS);
    }

    @Test
    @DisplayName("resendActivation posts the email as JSON, forwarding the client origin, and accepts 202")
    void resendAccepts202() {
        stubStatus(RESEND, 202);

        adapter.resendActivation(resend());

        verify(postRequestedFor(urlEqualTo(RESEND))
                .withHeader("X-Forwarded-For", equalTo(CLIENT_ADDRESS))
                .withRequestBody(matchingJsonPath("$.email", equalTo("user@example.com"))));
    }

    @Test
    @DisplayName("resendActivation maps 400 to InvalidRequestException")
    void resendMaps400() {
        stubStatus(RESEND, 400);

        assertThatThrownBy(() -> adapter.resendActivation(resend()))
                .isInstanceOf(AuthApiClient.InvalidRequestException.class);
    }

    @Test
    @DisplayName("resendActivation maps 429 to RateLimitedException carrying Retry-After")
    void resendMaps429() {
        stubFor(post(urlEqualTo(RESEND)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "45")));

        assertThatThrownBy(() -> adapter.resendActivation(resend()))
                .isInstanceOfSatisfying(AuthApiClient.RateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(45)));
    }

    @Test
    @DisplayName("resendActivation maps 503 to ServiceUnavailableException")
    void resendMaps503() {
        stubStatus(RESEND, 503);

        assertThatThrownBy(() -> adapter.resendActivation(resend()))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("resendActivation reports a transport fault as unavailable, without echoing the email")
    void resendMapsTransportFault() {
        stubFor(post(urlEqualTo(RESEND)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter.resendActivation(resend()))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("user@example.com");
    }
}
