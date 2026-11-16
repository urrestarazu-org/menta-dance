package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.SubscriptionHistoryEntry;
import com.menta.bff.application.dto.SubscriptionHistoryEntry.Status;
import com.menta.bff.application.dto.SubscriptionHistoryEntry.Type;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

/** {@code GET /api/v1/billing/subscriptions/me/history} through {@link SubscriptionApiAdapter} (#363). */
@WireMockTest
@DisplayName("SubscriptionApiAdapter subscription history")
class SubscriptionHistoryApiAdapterTest {

    private static final String HISTORY = "/api/v1/billing/subscriptions/me/history";
    private static final String TOKEN = "access-token-abc";

    private SubscriptionApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        adapter = adapterWith(wireMock, Duration.ofSeconds(1));
    }

    private static SubscriptionApiAdapter adapterWith(WireMockRuntimeInfo wireMock, Duration timeout) {
        BillingApiProperties properties = new BillingApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(timeout);
        return new SubscriptionApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stubHistory(int status, String body) {
        stubFor(get(urlEqualTo(HISTORY)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", status < 300 ? "application/json" : "application/problem+json")
                .withBody(body)));
    }

    @Test
    @DisplayName("maps every item in API order, ignores unknown fields and sends the Bearer token")
    void mapsItems() {
        stubHistory(200,
                """
                [{"id": "s-2", "planId": "p-2", "status": "ACTIVE", "startDate": "2026-11-01T03:00:00Z",
                  "endDate": "2026-12-01T03:00:00Z", "type": "TRIAL", "addedLater": 1},
                 {"id": "s-1", "planId": "p-1", "status": "CANCELLED", "startDate": "2026-10-01T03:00:00Z",
                  "endDate": "2026-10-31T03:00:00Z", "type": "PAID"}]
                """);

        List<SubscriptionHistoryEntry> history = adapter.listSubscriptionHistory(TOKEN);

        assertThat(history).containsExactly(
                new SubscriptionHistoryEntry("p-2", Status.ACTIVE, Instant.parse("2026-11-01T03:00:00Z"),
                        Instant.parse("2026-12-01T03:00:00Z"), Type.TRIAL),
                new SubscriptionHistoryEntry("p-1", Status.CANCELLED, Instant.parse("2026-10-01T03:00:00Z"),
                        Instant.parse("2026-10-31T03:00:00Z"), Type.PAID));
        verify(getRequestedFor(urlEqualTo(HISTORY)).withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    @DisplayName("a PENDING item keeps its null dates")
    void pendingWithoutDates() {
        stubHistory(200,
                "[{\"id\":\"s-1\",\"planId\":\"p-1\",\"status\":\"PENDING\",\"startDate\":null,\"endDate\":null,"
                        + "\"type\":\"PAID\"}]");

        assertThat(adapter.listSubscriptionHistory(TOKEN))
                .containsExactly(new SubscriptionHistoryEntry("p-1", Status.PENDING, null, null, Type.PAID));
    }

    @Test
    @DisplayName("a status or type this BFF does not know maps to UNKNOWN instead of failing the list")
    void unknownValues() {
        stubHistory(200,
                "[{\"planId\":\"p-1\",\"status\":\"SUSPENDED\",\"startDate\":null,\"endDate\":null,"
                        + "\"type\":\"GIFT\"}]");

        assertThat(adapter.listSubscriptionHistory(TOKEN))
                .containsExactly(new SubscriptionHistoryEntry("p-1", Status.UNKNOWN, null, null, Type.UNKNOWN));
    }

    @Test
    @DisplayName("an empty array means no subscriptions")
    void empty() {
        stubHistory(200, "[]");

        assertThat(adapter.listSubscriptionHistory(TOKEN)).isEmpty();
    }

    @ParameterizedTest(name = "a {0} answer fails closed")
    @ValueSource(ints = {400, 401, 403, 404, 429, 500, 503})
    void nonSuccessFailsClosed(int status) {
        stubHistory(status, "{\"status\": " + status + ", \"code\": \"SECRET_DETAIL\"}");

        assertThatThrownBy(() -> adapter.listSubscriptionHistory(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("SECRET_DETAIL")
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "an unusable 200 body fails closed: {0}")
    @ValueSource(strings = {
        "not json",
        "",
        "{\"planId\": \"p-1\"}",
        "[null]",
        "[{\"status\":\"ACTIVE\",\"startDate\":null,\"endDate\":null,\"type\":\"PAID\"}]",
        "[{\"planId\":\"p-1\",\"startDate\":null,\"endDate\":null,\"type\":\"PAID\"}]",
        "[{\"planId\":\"p-1\",\"status\":\"ACTIVE\",\"startDate\":null,\"endDate\":null}]",
        "[{\"planId\":\"p-1\",\"status\":\"ACTIVE\",\"startDate\":\"yesterday\",\"endDate\":null,\"type\":\"PAID\"}]"
    })
    void unusableBodyFailsClosed(String body) {
        stubHistory(200, body);

        assertThatThrownBy(() -> adapter.listSubscriptionHistory(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a slow answer fails closed once the configured timeout elapses")
    void timeoutFailsClosed(WireMockRuntimeInfo wireMock) {
        SubscriptionApiAdapter impatient = adapterWith(wireMock, Duration.ofMillis(100));
        stubFor(get(urlEqualTo(HISTORY)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("[]").withFixedDelay(1000)));

        assertThatThrownBy(() -> impatient.listSubscriptionHistory(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }
}
