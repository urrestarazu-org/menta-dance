package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.AssignedSession;
import com.menta.bff.application.port.out.PhysicalSessionsApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("PhysicalSessionsApiAdapter")
class PhysicalSessionsApiAdapterTest {

    private static final String MY_SESSIONS = "/api/v1/physical/sessions/me";
    private static final String TOKEN = "access-token-abc";
    private static final Instant FROM = Instant.parse("2026-10-13T03:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-12T03:00:00Z");

    private static final String TWO_SESSIONS_BODY =
            """
            {"from": "2026-10-13T03:00:00Z", "to": "2026-12-12T03:00:00Z", "sessions": [
              {"sessionId": "s-1", "courseId": "c-1", "courseName": "Salsa", "instructorName": "María",
               "scheduledAt": "2026-10-20T21:00:00Z", "checkInOpen": false},
              {"sessionId": "s-2", "courseId": "c-1", "courseName": "Salsa", "instructorName": "María",
               "scheduledAt": "2026-10-27T21:00:00Z", "checkInOpen": false}]}
            """;

    private PhysicalSessionsApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        VirtualApiProperties properties = new VirtualApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(Duration.ofMillis(500));
        adapter = new PhysicalSessionsApiAdapter(
                WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stubBody(int status, String body) {
        stubFor(get(urlPathEqualTo(MY_SESSIONS)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    @DisplayName("sends the bearer token and returns the ids of the student's sessions")
    void mapsSessionIds() {
        stubBody(200, TWO_SESSIONS_BODY);

        assertThat(adapter.findMySessionIds(TOKEN, FROM, TO)).containsExactlyInAnyOrder("s-1", "s-2");
        verify(getRequestedFor(urlPathEqualTo(MY_SESSIONS)).withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withQueryParam("from", equalTo("2026-10-13T03:00:00Z"))
                .withQueryParam("to", equalTo("2026-12-12T03:00:00Z")));
    }

    @Test
    @DisplayName("findMySessions maps id, class name and scheduled instant")
    void mapsSessions() {
        stubBody(200, TWO_SESSIONS_BODY);

        assertThat(adapter.findMySessions(TOKEN, FROM, TO)).containsExactly(
                new AssignedSession("s-1", "Salsa", Instant.parse("2026-10-20T21:00:00Z")),
                new AssignedSession("s-2", "Salsa", Instant.parse("2026-10-27T21:00:00Z")));
        verify(getRequestedFor(urlPathEqualTo(MY_SESSIONS)).withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withQueryParam("from", equalTo("2026-10-13T03:00:00Z"))
                .withQueryParam("to", equalTo("2026-12-12T03:00:00Z")));
    }

    @Test
    @DisplayName("both queries send the window as ISO-8601 UTC instants with Z")
    void sendsTheWindowAsUtcInstants() {
        stubBody(200, TWO_SESSIONS_BODY);

        Instant from = Instant.parse("2026-11-12T15:30:00Z");
        Instant to = Instant.parse("2026-12-13T15:30:00Z");

        adapter.findMySessionIds(TOKEN, from, to);
        adapter.findMySessions(TOKEN, from, to);

        verify(2, getRequestedFor(urlPathEqualTo(MY_SESSIONS))
                .withQueryParam("from", equalTo("2026-11-12T15:30:00Z"))
                .withQueryParam("to", equalTo("2026-12-13T15:30:00Z")));
    }

    @Test
    @DisplayName("findMySessions skips items without id or time and tolerates a missing class name")
    void sessionsSkipUnusableItems() {
        stubBody(200, "{\"sessions\": [{\"courseId\": \"c-1\"}, {\"sessionId\": \"s-0\"}, "
                + "{\"sessionId\": \"s-1\", \"scheduledAt\": \"2026-10-20T21:00:00Z\"}]}");

        assertThat(adapter.findMySessions(TOKEN, FROM, TO))
                .containsExactly(new AssignedSession("s-1", null, Instant.parse("2026-10-20T21:00:00Z")));
    }

    @Test
    @DisplayName("findMySessions is unavailable on an error status or an unusable body")
    void sessionsUnavailable() {
        stubBody(503, "{}");
        assertThatThrownBy(() -> adapter.findMySessions(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalSessionsApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);

        stubBody(200, "{\"from\": \"2026-10-13T03:00:00Z\"}");
        assertThatThrownBy(() -> adapter.findMySessions(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalSessionsApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("an empty list of sessions is an empty set")
    void emptyList() {
        stubBody(200, "{\"from\": \"2026-10-13T03:00:00Z\", \"to\": \"2026-12-12T03:00:00Z\", \"sessions\": []}");

        assertThat(adapter.findMySessionIds(TOKEN, FROM, TO)).isEmpty();
    }

    @Test
    @DisplayName("items without a session id are skipped")
    void skipsItemsWithoutId() {
        stubBody(200, "{\"sessions\": [{\"courseId\": \"c-1\"}, {\"sessionId\": \"s-1\"}]}");

        assertThat(adapter.findMySessionIds(TOKEN, FROM, TO)).containsExactly("s-1");
    }

    @ParameterizedTest(name = "status {0} is unavailable")
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    @DisplayName("an error status is unavailable, never a leak of the token")
    void errorStatuses(int status) {
        stubBody(status, "{\"status\":" + status + "}");

        assertThatThrownBy(() -> adapter.findMySessionIds(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalSessionsApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("an unreadable or empty body is unavailable")
    void unusableBodies() {
        stubBody(200, "<html>not json</html>");
        assertThatThrownBy(() -> adapter.findMySessionIds(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalSessionsApiClient.ServiceUnavailableException.class);

        stubBody(200, "");
        assertThatThrownBy(() -> adapter.findMySessionIds(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalSessionsApiClient.ServiceUnavailableException.class);

        stubBody(200, "{\"from\": \"2026-10-13T03:00:00Z\"}");
        assertThatThrownBy(() -> adapter.findMySessionIds(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalSessionsApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a dropped connection and a response slower than the timeout are unavailable")
    void transportErrorsAndTimeout() {
        stubFor(get(urlPathEqualTo(MY_SESSIONS)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.findMySessionIds(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalSessionsApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);

        stubFor(get(urlPathEqualTo(MY_SESSIONS)).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)
                .withHeader("Content-Type", "application/json").withBody("{\"sessions\": []}")));
        assertThatThrownBy(() -> adapter.findMySessionIds(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalSessionsApiClient.ServiceUnavailableException.class);
    }
}
