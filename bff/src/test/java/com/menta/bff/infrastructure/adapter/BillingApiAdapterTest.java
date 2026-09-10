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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapter fetch, status-mapping, and caching tests (PR 2 tasks 2.1-2.5, PR 3
 * tasks 3.1-3.5). The fetch/status-mapping tests above use a {@code cacheTtl}
 * of 5 minutes purely as an inert constructor argument and each proves a
 * single call in isolation; the {@code -- caching --} section below exercises
 * the single-entry TTL cache and its single-flight refresh, constructing a
 * fresh adapter per case with an explicit {@code cacheTtl} (per design: no
 * {@code Clock} injection — {@code Duration.ofMinutes(5)} proves the
 * within-TTL case, {@code Duration.ZERO} deterministically forces the
 * always-expired branch, no sleeping or time mocking needed).
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

    // -- caching --

    @Test
    @DisplayName("should serve repeated requests within the TTL from cache, triggering exactly one upstream call")
    void shouldServeRepeatedRequestsWithinTtlFromCache() {
        // Given — the default setUp() adapter already carries a 5-minute cacheTtl.
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(plansResponseBody()))));

        // When
        List<PlanSummary> first = billingApiAdapter.getPlans();
        List<PlanSummary> second = billingApiAdapter.getPlans();

        // Then
        assertThat(second).isEqualTo(first);
        verify(1, getRequestedFor(urlEqualTo(PLANS_PATH)));
    }

    @Test
    @DisplayName("should trigger a fresh upstream call on every request when the cache TTL is zero")
    void shouldTriggerFreshUpstreamCallEveryTimeWhenCacheTtlIsZero() {
        // Given
        BillingApiAdapter zeroTtlAdapter = adapterWithTtl(Duration.ZERO);
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(plansResponseBody()))));

        // When
        zeroTtlAdapter.getPlans();
        zeroTtlAdapter.getPlans();

        // Then
        verify(2, getRequestedFor(urlEqualTo(PLANS_PATH)));
    }

    @Test
    @DisplayName("should NOT serve an expired entry when the refresh fails — it must throw instead")
    void shouldNotServeExpiredEntryWhenRefreshFails() {
        // Given — the corrected, locked behavior (spec: "An expired entry is not
        // served when the refresh fails"). A zero TTL makes the first entry
        // expired the instant it is written, so the second call always refreshes.
        BillingApiAdapter zeroTtlAdapter = adapterWithTtl(Duration.ZERO);
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(plansResponseBody()))));

        List<PlanSummary> firstResult = zeroTtlAdapter.getPlans();
        assertThat(firstResult).isNotEmpty();

        // Re-stub to force the refresh to fail.
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Service temporarily unavailable\"}")));

        // When / Then — must throw, NOT silently return firstResult.
        assertThatThrownBy(zeroTtlAdapter::getPlans)
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("should throw and leave the cache empty when the upstream call fails on a cold cache")
    void shouldThrowAndLeaveCacheEmptyWhenColdCacheCallFails() {
        // Given — empty cache, first call fails.
        BillingApiAdapter adapter = adapterWithTtl(Duration.ofMinutes(5));
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Service temporarily unavailable\"}")));

        assertThatThrownBy(adapter::getPlans)
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);

        // Then — nothing corrupted the cache state: a subsequent successful call
        // still works normally.
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(plansResponseBody()))));

        List<PlanSummary> result = adapter.getPlans();
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("should single-flight concurrent cold-cache calls into exactly one upstream request")
    void shouldSingleFlightConcurrentColdCacheCalls() throws InterruptedException {
        // Given — a cold cache and a delayed upstream response, so N concurrent
        // callers race each other before any of them has populated the cache.
        int threadCount = 8;
        BillingApiAdapter adapter = adapterWithTtl(Duration.ofMinutes(5));
        stubFor(get(urlEqualTo(PLANS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(plansResponseBody()))));

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Future<List<PlanSummary>>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();
                        return adapter.getPlans();
                    }))
                    .toList();

            readyLatch.await();
            startLatch.countDown();

            List<List<PlanSummary>> results = new ArrayList<>();
            for (Future<List<PlanSummary>> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            // Then
            verify(1, getRequestedFor(urlEqualTo(PLANS_PATH)));
            Set<List<PlanSummary>> distinctResults = new HashSet<>(results);
            assertThat(distinctResults).hasSize(1);
            assertThat(results).allSatisfy(result -> assertThat(result).hasSize(1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executorService.shutdownNow();
        }
    }

    private BillingApiAdapter adapterWithTtl(Duration cacheTtl) {
        BillingApiProperties properties = new BillingApiProperties();
        properties.setBaseUrl(baseUrl);
        properties.setTimeout(Duration.ofSeconds(5));
        properties.setCacheTtl(cacheTtl);

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

        return new BillingApiAdapter(webClient, properties);
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
