package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.app.integration.support.CatalogAccessMocksIntegrationTestBase;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * MySQL-backed HTTP integration coverage for the public plans endpoints
 * (US-BILLING-001), covering the 5 BDD escenarios: active plans listed and
 * ordered, featured flag, empty list on 200, full detail, 404 on missing/
 * inactive.
 */
class BillingPlansIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

    @Autowired private TestRestTemplate http;
    @Autowired private PlanJpaRepository planJpaRepository;
    @Autowired private PlanCourseJpaRepository planCourseJpaRepository;

    @AfterEach
    void cleanUp() {
        planCourseJpaRepository.deleteAll();
        planJpaRepository.deleteAll();
    }

    private void allowRateLimit() {
        when(billingPlansRateLimitPort.consume(any())).thenReturn(RateLimitDecision.allowed());
    }

    private UUID seedPlan(String name, BigDecimal price, boolean featured, PlanStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        planJpaRepository.save(new PlanJpaEntity(
            id, name, "desc " + name, price, "ARS", 30, featured, status,
            "terms", "cancellation", now, now
        ));
        return id;
    }

    @Test
    void lists_only_active_plans_ordered_by_price_ascending() {
        allowRateLimit();
        seedPlan("Anual", new BigDecimal("100000.00"), false, PlanStatus.ACTIVE);
        seedPlan("Inactivo", new BigDecimal("1.00"), false, PlanStatus.INACTIVE);
        seedPlan("Mensual", new BigDecimal("15000.00"), true, PlanStatus.ACTIVE);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/plans", HttpMethod.GET, null, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> plans = (List<Map<String, Object>>) response.getBody().get("plans");
        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).get("name")).isEqualTo("Mensual");
        assertThat(plans.get(0).get("featured")).isEqualTo(true);
        assertThat(plans.get(1).get("name")).isEqualTo("Anual");
    }

    @Test
    void returns_200_with_an_empty_list_when_there_are_no_active_plans() {
        allowRateLimit();

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/plans", HttpMethod.GET, null, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("plans")).isEmpty();
    }

    @Test
    void get_returns_the_full_detail_of_an_active_plan_including_resolved_course_names() {
        allowRateLimit();
        UUID id = seedPlan("Mensual", new BigDecimal("15000.00"), true, PlanStatus.ACTIVE);
        planCourseJpaRepository.save(new PlanCourseJpaEntity(id, "course-1"));
        when(courseCatalogPort.courseName("course-1")).thenReturn(Optional.of("Tango Basico"));

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/plans/" + id, HttpMethod.GET, null, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("termsAndConditions")).isEqualTo("terms");
        List<Map<String, Object>> courses = (List<Map<String, Object>>) response.getBody().get("courses");
        assertThat(courses.get(0).get("name")).isEqualTo("Tango Basico");
    }

    @Test
    void get_an_inactive_plan_returns_404_without_distinguishing_it_from_unknown() {
        allowRateLimit();
        UUID inactiveId = seedPlan("Inactivo", BigDecimal.TEN, false, PlanStatus.INACTIVE);

        ResponseEntity<Map> inactiveResponse = http.exchange(
            "/api/v1/billing/plans/" + inactiveId, HttpMethod.GET, null, Map.class
        );
        ResponseEntity<Map> unknownResponse = http.exchange(
            "/api/v1/billing/plans/" + UUID.randomUUID(), HttpMethod.GET, null, Map.class
        );

        assertThat(inactiveResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(inactiveResponse.getBody().get("code")).isEqualTo(unknownResponse.getBody().get("code"));
    }

    @Test
    void an_exhausted_rate_limit_budget_returns_429_with_retry_after() {
        when(billingPlansRateLimitPort.consume(any()))
            .thenReturn(RateLimitDecision.limited(Duration.ofSeconds(20)));

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/plans", HttpMethod.GET, null, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("20");
    }

    @Test
    void the_public_plans_endpoint_requires_no_authentication() {
        allowRateLimit();

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/plans", HttpMethod.GET, null, Map.class
        );

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
