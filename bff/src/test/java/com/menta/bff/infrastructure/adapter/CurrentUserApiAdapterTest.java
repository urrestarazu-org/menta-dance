package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("CurrentUserApiAdapter")
class CurrentUserApiAdapterTest {

    private static final String ME = "/api/v1/users/me";
    private static final String TOKEN = "access-token-abc";
    private static final String SECRET = "secret-detail";

    /** Generous on purpose: the first request of a cold JVM on a slow CI runner can take over a second. */
    private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    private CurrentUserApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        adapter = adapterAt(wireMock.getHttpBaseUrl(), GENEROUS_TIMEOUT);
    }

    private static CurrentUserApiAdapter adapterAt(String baseUrl, Duration timeout) {
        AuthProperties properties = new AuthProperties();
        properties.setBaseUrl(baseUrl);
        properties.setTimeout(timeout);
        return new CurrentUserApiAdapter(WebClient.builder().baseUrl(baseUrl).build(), properties);
    }

    private static void stubMe(int status, String body) {
        stubFor(get(urlPathEqualTo(ME)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static String me(String firstName, String lastName) {
        return ("{\"userId\":\"0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00\",\"firstName\":%s,\"lastName\":%s,"
                + "\"role\":\"INSTRUCTOR\"}").formatted(firstName, lastName);
    }

    @Test
    @DisplayName("sends the bearer token to the current-user endpoint and joins both names")
    void joinsBothNames() {
        stubMe(200, me("\"Ada\"", "\"Lovelace\""));

        assertThat(adapter.displayNameOf(TOKEN)).contains("Ada Lovelace");
        verify(1, getRequestedFor(urlPathEqualTo(ME)).withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    @DisplayName("strips each part before joining them")
    void stripsTheParts() {
        stubMe(200, me("\"  Ada \"", "\" Lovelace  \""));

        assertThat(adapter.displayNameOf(TOKEN)).contains("Ada Lovelace");
    }

    @Test
    @DisplayName("a missing or blank part is dropped, leaving the other one alone")
    void dropsAMissingPart() {
        stubMe(200, me("\"Ada\"", "null"));
        assertThat(adapter.displayNameOf(TOKEN)).contains("Ada");

        stubMe(200, me("\"  \"", "\"Lovelace\""));
        assertThat(adapter.displayNameOf(TOKEN)).contains("Lovelace");
    }

    @Test
    @DisplayName("an account without a profile (both names null) has no display name")
    void bothNamesNull() {
        stubMe(200, me("null", "null"));

        assertThat(adapter.displayNameOf(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("blank names, or no name fields at all, give no display name")
    void blankNames() {
        stubMe(200, me("\"   \"", "\"\""));
        assertThat(adapter.displayNameOf(TOKEN)).isEmpty();

        stubMe(200, "{\"userId\":\"u-1\",\"role\":\"INSTRUCTOR\"}");
        assertThat(adapter.displayNameOf(TOKEN)).isEmpty();
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    @DisplayName("any answer other than a 200 gives no name, and neither the token nor the body reaches the log")
    void nonSuccessIsEmpty(int status, CapturedOutput output) {
        stubFor(get(urlPathEqualTo(ME)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":%d,\"detail\":\"%s\",\"code\":\"X\"}".formatted(status, SECRET))));

        assertThat(adapter.displayNameOf(TOKEN)).isEmpty();
        assertThat(output.getAll()).contains("Auth API current user call").contains(String.valueOf(status))
                .doesNotContain(TOKEN).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("a 200 with a body that is not valid JSON gives no name, without logging the body")
    void malformedBodyIsEmpty(CapturedOutput output) {
        stubMe(200, "{\"firstName\":\"" + SECRET + "\", oops");

        assertThat(adapter.displayNameOf(TOKEN)).isEmpty();
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("a 200 without a body gives no name")
    void emptyBodyIsEmpty() {
        stubFor(get(urlPathEqualTo(ME)).willReturn(aResponse().withStatus(200)));

        assertThat(adapter.displayNameOf(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("a dropped connection gives no name")
    void droppedConnectionIsEmpty(CapturedOutput output) {
        stubFor(get(urlPathEqualTo(ME)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(adapter.displayNameOf(TOKEN)).isEmpty();
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("an API that refuses the connection gives no name")
    void refusedConnectionIsEmpty() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        CurrentUserApiAdapter unreachable = adapterAt("http://localhost:" + closedPort, GENEROUS_TIMEOUT);

        assertThat(unreachable.displayNameOf(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("an API slower than the timeout gives no name")
    void timeoutIsEmpty(WireMockRuntimeInfo wireMock) {
        stubFor(get(urlPathEqualTo(ME)).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)
                .withHeader("Content-Type", "application/json").withBody(me("\"Ada\"", "\"Lovelace\""))));

        assertThat(adapterAt(wireMock.getHttpBaseUrl(), SHORT_TIMEOUT).displayNameOf(TOKEN)).isEmpty();
    }
}
