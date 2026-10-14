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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.CurrentSubscription;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("SubscriptionApiAdapter")
class SubscriptionApiAdapterTest {

    private static final String ME = "/api/v1/billing/subscriptions/me";
    private static final String TOKEN = "access-token-abc";

    private SubscriptionApiAdapter adapter;
    private ListAppender<ILoggingEvent> logs;
    private Logger rootLogger;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        BillingApiProperties properties = new BillingApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(Duration.ofSeconds(1));
        adapter = new SubscriptionApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(),
                properties);
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>();
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(logs);
    }

    private static void stubMe(int status, String body) {
        stubFor(get(urlEqualTo(ME)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", status < 300 ? "application/json" : "application/problem+json")
                .withBody(body)));
    }

    @Test
    @DisplayName("an ACTIVE subscription maps its end date and sends the bearer token")
    void active() {
        stubMe(200,
                """
                {"subscriptionId": "s-1", "planId": "p-1", "status": "ACTIVE",
                "startDate": "2026-10-01T00:00:00Z", "endDate": "2026-10-31T00:00:00Z",
                "daysRemaining": 17, "expiringSoon": false, "checkoutUrl": null, "plansUrl": null}
                """);

        assertThat(adapter.getCurrentSubscription(TOKEN))
                .isEqualTo(new CurrentSubscription.Active(Instant.parse("2026-10-31T00:00:00Z")));
        verify(getRequestedFor(urlEqualTo(ME)).withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    @DisplayName("a PENDING subscription maps the full subscription id")
    void pending() {
        stubMe(200,
                """
                {"subscriptionId": "0f0e0d0c-1111-2222-3333-444444444444", "planId": "p-1", "status": "PENDING",
                "startDate": null, "endDate": null, "checkoutUrl": "https://pay.example.com/c/1"}
                """);

        assertThat(adapter.getCurrentSubscription(TOKEN))
                .isEqualTo(new CurrentSubscription.Pending("0f0e0d0c-1111-2222-3333-444444444444"));
    }

    @Test
    @DisplayName("an EXPIRED subscription maps its end date")
    void expired() {
        stubMe(200,
                """
                {"subscriptionId": "s-1", "planId": "p-1", "status": "EXPIRED",
                "startDate": "2026-08-01T00:00:00Z", "endDate": "2026-08-31T00:00:00Z",
                "plansUrl": "/api/v1/billing/plans"}
                """);

        assertThat(adapter.getCurrentSubscription(TOKEN))
                .isEqualTo(new CurrentSubscription.Expired(Instant.parse("2026-08-31T00:00:00Z")));
    }

    @Test
    @DisplayName("404 NO_SUBSCRIPTION means the student has no subscription")
    void noSubscription() {
        stubMe(404, "{\"status\":404,\"code\":\"NO_SUBSCRIPTION\"}");

        assertThat(adapter.getCurrentSubscription(TOKEN)).isEqualTo(new CurrentSubscription.None());
    }

    @ParameterizedTest(name = "{0} {1} fails closed")
    @CsvSource({
        "404, SOMETHING_ELSE",
        "401, UNAUTHORIZED",
        "429, TOO_MANY_REQUESTS",
        "500, INTERNAL",
        "503, UNAVAILABLE",
        "418, TEAPOT"})
    void failsClosedOnAnyOtherStatus(int status, String code) {
        stubMe(status, "{\"status\":" + status + ",\"code\":\"" + code + "\"}");

        assertThatThrownBy(() -> adapter.getCurrentSubscription(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "unreadable 200 body {0} fails closed")
    @CsvSource(delimiter = '|', value = {
        "unknown status|{\"subscriptionId\":\"s\",\"status\":\"WEIRD\"}",
        "active without end date|{\"subscriptionId\":\"s\",\"status\":\"ACTIVE\"}",
        "expired without end date|{\"subscriptionId\":\"s\",\"status\":\"EXPIRED\"}",
        "pending without id|{\"status\":\"PENDING\"}",
        "no status|{\"subscriptionId\":\"s\"}"})
    void failsClosedOnIncompleteBody(String label, String body) {
        stubMe(200, body);

        assertThatThrownBy(() -> adapter.getCurrentSubscription(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a transport fault fails closed and the token never reaches a log line")
    void transportFaultNeverLogsToken() {
        stubFor(get(urlEqualTo(ME)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter.getCurrentSubscription(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
        assertThat(logs.list).isNotEmpty()
                .noneMatch(event -> event.getFormattedMessage().contains(TOKEN));
    }

    @Test
    @DisplayName("a response slower than the timeout fails closed")
    void timeoutFailsClosed() {
        stubFor(get(urlEqualTo(ME)).willReturn(aResponse().withStatus(200).withFixedDelay(3000)));

        assertThatThrownBy(() -> adapter.getCurrentSubscription(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }
}
