package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.PhysicalCoursePricing;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("BillingApiAdapter physical course pricing")
class BillingApiAdapterPricingTest {

    private static final String PATH = "/api/v1/billing/physical/courses/course-1/pricing";
    private static final String BODY =
            """
            {"courseId": "course-1", "monthlyPrice": 25000.00, "currency": "ARS",
             "individualSurchargePercent": 20, "version": 3, "updatedAt": "2026-10-10T12:00:00Z"}
            """;

    private BillingApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        BillingApiProperties properties = new BillingApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(Duration.ofSeconds(2));
        properties.setCacheTtl(Duration.ofMinutes(5));
        adapter = new BillingApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stubStatus(String path, int status, String body) {
        stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    @DisplayName("returns the monthly price and currency, sending no Authorization header")
    void published() {
        stubStatus(PATH, 200, BODY);

        PhysicalCoursePricing pricing = adapter.getPhysicalCoursePricing("course-1");

        assertThat(pricing).isEqualTo(new PhysicalCoursePricing("course-1", new BigDecimal("25000.00"), "ARS"));
        verify(getRequestedFor(urlEqualTo(PATH)).withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("the course id travels as one encoded path segment")
    void courseIdIsEncoded() {
        stubStatus("/api/v1/billing/physical/courses/a%2Fb%3Fc/pricing", 200, BODY);

        assertThat(adapter.getPhysicalCoursePricing("a/b?c").currency()).isEqualTo("ARS");
    }

    @Test
    @DisplayName("404 means no price is published")
    void notPublished() {
        stubStatus(PATH, 404, "{\"status\":404,\"code\":\"PHYSICAL_COURSE_PRICING_NOT_FOUND\"}");

        assertThatThrownBy(() -> adapter.getPhysicalCoursePricing("course-1"))
                .isInstanceOf(BillingApiClient.NotFoundException.class)
                .hasMessageNotContaining("PRICING_NOT_FOUND");
    }

    @ParameterizedTest(name = "status {0} is unavailable")
    @ValueSource(ints = {400, 401, 429, 500, 503})
    void otherStatusesAreUnavailable(int status) {
        stubStatus(PATH, status, "{\"title\":\"internal detail\"}");

        assertThatThrownBy(() -> adapter.getPhysicalCoursePricing("course-1"))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("internal detail");
    }

    @Test
    @DisplayName("429 carries Retry-After seconds")
    void retryAfter() {
        stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));

        assertThatThrownBy(() -> adapter.getPhysicalCoursePricing("course-1"))
                .isInstanceOfSatisfying(BillingApiClient.ServiceUnavailableException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(30L));
    }

    @ParameterizedTest(name = "unreadable 200 body [{0}] is unavailable")
    @ValueSource(strings = {"<html>oops</html>", "", "{}", "{\"currency\":\"ARS\"}", "{\"monthlyPrice\":10}"})
    void unreadableBodies(String body) {
        stubStatus(PATH, 200, body);

        assertThatThrownBy(() -> adapter.getPhysicalCoursePricing("course-1"))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a dropped connection is unavailable")
    void transportError() {
        stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter.getPhysicalCoursePricing("course-1"))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("the price is never cached: every call reaches the API")
    void notCached() {
        stubStatus(PATH, 200, BODY);

        adapter.getPhysicalCoursePricing("course-1");
        adapter.getPhysicalCoursePricing("course-1");

        verify(2, getRequestedFor(urlEqualTo(PATH)));
    }
}
