package com.menta.bff.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapter fetch and status-mapping tests only (PR 2, tasks 2.1-2.5) — no
 * caching behavior yet. Every test uses a {@code cacheTtl} of 5 minutes
 * purely as an inert constructor argument; caching itself is out of scope
 * until PR 3, so each test proves a single call in isolation.
 */
@WireMockTest
@DisplayName("BillingApiAdapter")
class BillingApiAdapterTest {

    private static final String PLANS_PATH = "/api/v1/billing/plans";

    private BillingApiAdapter billingApiAdapter;
    private ObjectMapper objectMapper;
    private String baseUrl;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        baseUrl = wmRuntimeInfo.getHttpBaseUrl();

        BillingApiProperties billingApiProperties = new BillingApiProperties();
        billingApiProperties.setBaseUrl(baseUrl);
        billingApiProperties.setTimeout(Duration.ofSeconds(5));
        billingApiProperties.setCacheTtl(Duration.ofMinutes(5));

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

        objectMapper = new ObjectMapper();
        billingApiAdapter = new BillingApiAdapter(webClient, billingApiProperties);
    }

    @Test
    @DisplayName("should return the mapped plan list when upstream responds with 200, ignoring an unknown courses field")
    void shouldReturnMappedPlanListWhenUpstreamRespondsWith200() {
        // Given
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(plansResponseBody()))));

        // When
        List<PlanSummary> result = billingApiAdapter.getPlans();

        // Then
        assertThat(result).hasSize(1);
        PlanSummary plan = result.get(0);
        assertThat(plan.id()).isEqualTo("plan-1");
        assertThat(plan.name()).isEqualTo("Plan Mensual");
        assertThat(plan.description()).isEqualTo("Acceso mensual completo");
        assertThat(plan.price()).isEqualByComparingTo("1999.99");
        assertThat(plan.currency()).isEqualTo("ARS");
        assertThat(plan.durationDays()).isEqualTo(30);
        assertThat(plan.featured()).isTrue();
    }

    @Test
    @DisplayName("should never send an Authorization header on the plans call")
    void shouldNeverSendAuthorizationHeaderOnThePlansCall() {
        // Given
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(plansResponseBody()))));

        // When
        // getPlans() takes no token parameter at all — this is structural,
        // not conditional (billing-api-integration spec).
        billingApiAdapter.getPlans();

        // Then
        verify(getRequestedFor(urlEqualTo(PLANS_PATH))
                .withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("should throw NotFoundException when upstream responds with 404")
    void shouldThrowNotFoundExceptionWhenUpstreamRespondsWith404() {
        // Given
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Plans not found\"}")));

        // When / Then
        assertThatThrownBy(() -> billingApiAdapter.getPlans())
                .isInstanceOf(BillingApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException capturing Retry-After when upstream responds with 429")
    void shouldThrowServiceUnavailableExceptionCapturingRetryAfterWhenUpstreamRespondsWith429() {
        // Given
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/problem+json")
                        .withHeader("Retry-After", "60")
                        .withBody("{\"detail\":\"Too many requests\"}")));

        // When / Then
        assertThatThrownBy(() -> billingApiAdapter.getPlans())
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .satisfies(exception -> assertThat(
                        ((BillingApiClient.ServiceUnavailableException) exception).getRetryAfterSeconds())
                        .isEqualTo(60L));
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException with no Retry-After when upstream responds with 503")
    void shouldThrowServiceUnavailableExceptionWithNoRetryAfterWhenUpstreamRespondsWith503() {
        // Given — proves retryAfterSeconds is genuinely optional, not always populated.
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Service temporarily unavailable\"}")));

        // When / Then
        assertThatThrownBy(() -> billingApiAdapter.getPlans())
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .satisfies(exception -> assertThat(
                        ((BillingApiClient.ServiceUnavailableException) exception).getRetryAfterSeconds())
                        .isNull());
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException when the call times out")
    void shouldThrowServiceUnavailableExceptionWhenTheCallTimesOut() {
        // Given — a fixed delay well past the adapter's timeout forces the
        // generic-exception fallback path (mirrors VirtualApiAdapter's handling
        // of a non-WebClientResponseException failure).
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(plansResponseBody()))));

        BillingApiProperties timeoutProperties = new BillingApiProperties();
        timeoutProperties.setBaseUrl(baseUrl);
        timeoutProperties.setTimeout(Duration.ofMillis(50));
        timeoutProperties.setCacheTtl(Duration.ofMinutes(5));

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

        BillingApiAdapter shortTimeoutAdapter = new BillingApiAdapter(webClient, timeoutProperties);

        // When / Then
        assertThatThrownBy(shortTimeoutAdapter::getPlans)
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    private Map<String, Object> plansResponseBody() {
        return Map.of(
                "plans", List.of(Map.of(
                        "id", "plan-1",
                        "name", "Plan Mensual",
                        "description", "Acceso mensual completo",
                        "price", 1999.99,
                        "currency", "ARS",
                        "durationDays", 30,
                        "featured", true,
                        "courses", List.of(Map.of("id", "course-1", "title", "Intro to Salsa"))
                ))
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
