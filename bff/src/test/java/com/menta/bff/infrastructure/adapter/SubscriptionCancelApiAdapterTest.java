package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import com.menta.bff.application.dto.CancelledSubscription;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

/** {@code DELETE /api/v1/billing/subscriptions/me} through {@link SubscriptionApiAdapter} (#363). */
@WireMockTest
@DisplayName("SubscriptionApiAdapter cancel subscription")
class SubscriptionCancelApiAdapterTest {

    private static final String ME = "/api/v1/billing/subscriptions/me";
    private static final String TOKEN = "access-token-abc";
    private static final String OK_BODY =
            """
            {"subscriptionId": "s-1", "status": "CANCELLED", "accessEndsAt": "2026-12-01T03:00:00Z",
             "cancellationPolicy": "Sin reembolso", "addedLater": 1}
            """;

    private SubscriptionApiAdapter adapter;
    private ListAppender<ILoggingEvent> logs;
    private Logger rootLogger;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        adapter = adapterWith(wireMock, Duration.ofSeconds(1));
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>();
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(logs);
    }

    private static SubscriptionApiAdapter adapterWith(WireMockRuntimeInfo wireMock, Duration timeout) {
        BillingApiProperties properties = new BillingApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(timeout);
        return new SubscriptionApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stubCancel(int status, String body) {
        stubFor(delete(urlEqualTo(ME)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", status < 300 ? "application/json" : "application/problem+json")
                .withBody(body)));
    }

    @Test
    @DisplayName("a 200 gives the end of the access, ignores unknown fields and sends DELETE with the Bearer token")
    void cancels() {
        stubCancel(200, OK_BODY);

        CancelledSubscription cancelled = adapter.cancelSubscription(TOKEN);

        assertThat(cancelled).isEqualTo(new CancelledSubscription(Instant.parse("2026-12-01T03:00:00Z")));
        verify(1, deleteRequestedFor(urlEqualTo(ME)).withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    @DisplayName("a 404 SUBSCRIPTION_NOT_FOUND means there was nothing to cancel")
    void nothingToCancel() {
        stubCancel(404, "{\"status\":404,\"code\":\"SUBSCRIPTION_NOT_FOUND\"}");

        assertThatThrownBy(() -> adapter.cancelSubscription(TOKEN))
                .isInstanceOf(SubscriptionApiClient.NoCancellableSubscriptionException.class);
    }

    @Test
    @DisplayName("a 409 SUBSCRIPTION_CONFLICT is a retryable conflict")
    void conflict() {
        stubCancel(409, "{\"status\":409,\"code\":\"SUBSCRIPTION_CONFLICT\"}");

        assertThatThrownBy(() -> adapter.cancelSubscription(TOKEN))
                .isInstanceOf(SubscriptionApiClient.SubscriptionConflictException.class);
    }

    @ParameterizedTest(name = "a {0} with code {1} fails closed")
    @CsvSource({
        "404, SOMETHING_ELSE", "409, SOMETHING_ELSE", "400, INVALID", "401, UNAUTHORIZED", "403, FORBIDDEN",
        "429, TOO_MANY", "500, SECRET_DETAIL", "503, UNAVAILABLE"
    })
    void failsClosed(int status, String code) {
        stubCancel(status, "{\"status\": " + status + ", \"code\": \"" + code + "\"}");

        assertThatThrownBy(() -> adapter.cancelSubscription(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(code)
                .hasMessageNotContaining(TOKEN);
        verify(1, deleteRequestedFor(urlEqualTo(ME)));
    }

    @Test
    @DisplayName("an error answer without a readable body still fails closed")
    void unreadableErrorBody() {
        stubCancel(404, "not json");

        assertThatThrownBy(() -> adapter.cancelSubscription(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @ParameterizedTest(name = "an unusable 200 body fails closed: {0}")
    @ValueSource(strings = {
        "not json",
        "",
        "{}",
        "{\"status\": \"CANCELLED\"}",
        "{\"accessEndsAt\": null}",
        "{\"accessEndsAt\": \"tomorrow\"}"
    })
    void unusableBodyFailsClosed(String body) {
        stubCancel(200, body);

        assertThatThrownBy(() -> adapter.cancelSubscription(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a failed DELETE is never retried")
    void notRetried() {
        stubCancel(503, "{}");

        assertThatThrownBy(() -> adapter.cancelSubscription(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);

        verify(1, deleteRequestedFor(urlEqualTo(ME)));
    }

    @Test
    @DisplayName("a slow answer fails closed once the configured timeout elapses, without a retry")
    void timeoutFailsClosed(WireMockRuntimeInfo wireMock) {
        SubscriptionApiAdapter impatient = adapterWith(wireMock, Duration.ofMillis(100));
        stubFor(delete(urlEqualTo(ME)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(OK_BODY).withFixedDelay(1000)));

        assertThatThrownBy(() -> impatient.cancelSubscription(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
        verify(1, deleteRequestedFor(urlEqualTo(ME)));
    }

    @Test
    @DisplayName("a transport fault fails closed and neither the token nor a body reaches a log line")
    void transportFaultNeverLogsToken() {
        stubFor(delete(urlEqualTo(ME)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter.cancelSubscription(TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
        assertThat(logs.list).isNotEmpty()
                .noneMatch(event -> event.getFormattedMessage().contains(TOKEN));
    }
}
