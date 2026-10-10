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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.PhysicalPurchaseType;
import com.menta.bff.application.dto.PhysicalQuote;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.math.BigDecimal;
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
@DisplayName("PhysicalPurchaseApiAdapter")
class PhysicalPurchaseApiAdapterTest {

    private static final String QUOTES = "/api/v1/billing/physical/quotes";
    private static final String QUOTE_ID = "00000000-0000-0000-0000-0000000000b1";
    private static final String TOKEN = "access-token-abc";
    private static final String BEARER = "Bearer " + TOKEN;

    private static final String QUOTE_BODY =
            """
            {"id": "%s", "courseId": "course-1", "purchaseType": "INDIVIDUAL", "scheduledSessionCount": 4,
             "selectedSessionId": "s-1", "amount": 12500.50, "currency": "ARS", "availability": "%s",
             "pricingVersion": 3, "createdAt": "2026-10-10T12:00:00Z", "expiresAt": "2026-10-10T13:00:00Z"}
            """;

    private PhysicalPurchaseApiAdapter adapter;
    private ListAppender<ILoggingEvent> logs;
    private Logger rootLogger;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        BillingApiProperties properties = new BillingApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(Duration.ofSeconds(2));
        adapter = new PhysicalPurchaseApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(),
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

    private static void stubStatus(com.github.tomakehurst.wiremock.client.MappingBuilder mapping, int status,
            String code) {
        stubFor(mapping.willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":" + status + ",\"code\":\"" + code + "\"}")));
    }

    @Test
    @DisplayName("an individual quote sends the bearer token and the session, and maps the response")
    void individualQuote() {
        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody(QUOTE_BODY.formatted(QUOTE_ID, "AVAILABLE"))));

        PhysicalQuote quote = adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.INDIVIDUAL, "s-1");

        assertThat(quote).isEqualTo(new PhysicalQuote(QUOTE_ID, PhysicalPurchaseType.INDIVIDUAL, 4, "s-1",
                new BigDecimal("12500.50"), "ARS", false, Instant.parse("2026-10-10T13:00:00Z")));
        verify(postRequestedFor(urlEqualTo(QUOTES)).withHeader("Authorization", equalTo(BEARER))
                .withRequestBody(matchingJsonPath("$.courseId", equalTo("course-1")))
                .withRequestBody(matchingJsonPath("$.purchaseType", equalTo("INDIVIDUAL")))
                .withRequestBody(matchingJsonPath("$.selectedSessionId", equalTo("s-1"))));
    }

    @Test
    @DisplayName("a monthly quote omits the session and reads UNAVAILABLE as unavailable")
    void monthlyQuoteUnavailable() {
        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(QUOTE_BODY.formatted(QUOTE_ID, "UNAVAILABLE"))));

        PhysicalQuote quote = adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null);

        assertThat(quote.unavailable()).isTrue();
        verify(postRequestedFor(urlEqualTo(QUOTES)).withRequestBody(matchingJsonPath("$.purchaseType",
                equalTo("MONTHLY"))).withRequestBody(matchingJsonPath("$[?(!@.selectedSessionId)]")));
    }

    @ParameterizedTest(name = "quote {0} {1} -> {2}")
    @CsvSource({
        "422, NO_SCHEDULED_SESSIONS, NoScheduledSessionsException",
        "422, SELECTED_SESSION_REQUIRED, InvalidRequestException",
        "422, INDIVIDUAL_SURCHARGE_TOO_SMALL, InvalidRequestException",
        "400, INVALID_REQUEST, InvalidRequestException",
        "404, PHYSICAL_COURSE_PRICING_NOT_FOUND, NotFoundException",
        "401, UNAUTHORIZED, ServiceUnavailableException",
        "409, CAPACITY_UNAVAILABLE, ServiceUnavailableException",
        "500, INTERNAL, ServiceUnavailableException",
        "418, TEAPOT, ServiceUnavailableException"})
    void quoteErrors(int status, String code, String expected) {
        stubStatus(post(urlEqualTo(QUOTES)), status, code);

        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(RuntimeException.class).hasMessageNotContaining(TOKEN)
                .satisfies(e -> assertThat(e.getClass().getSimpleName()).isEqualTo(expected));
    }

    @Test
    @DisplayName("429 carries the Retry-After seconds, and an unparsable value is dropped")
    void rateLimited() {
        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));

        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOfSatisfying(BillingApiClient.ServiceUnavailableException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(30L));

        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "soon")));

        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOfSatisfying(BillingApiClient.ServiceUnavailableException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isNull());
    }

    @Test
    @DisplayName("an empty or non-JSON error body and a dropped connection are unavailable, never a leak")
    void garbageAndTransportErrors() {
        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(502).withBody("<html>bad gateway</html>")));
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);

        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(200)));
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);

        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "course-1", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("the access token never reaches the logs, even when the call fails")
    void tokenIsNeverLogged() {
        stubStatus(post(urlEqualTo(QUOTES)), 500, "INTERNAL");
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "c", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(RuntimeException.class);
        stubFor(post(urlEqualTo(QUOTES)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.createQuote(TOKEN, "c", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(RuntimeException.class);

        assertThat(logs.list).isNotEmpty().allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain(TOKEN).doesNotContain("Authorization"));
    }
}
