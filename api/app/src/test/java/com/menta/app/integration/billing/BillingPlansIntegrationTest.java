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
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * inactive. Course names resolve against real Virtual and Physical rows
 * through the real {@code CourseCatalogPortAdapter} (#108); the
 * partial-failure cases stay at unit level because forcing a module to throw
 * here would need a spy, which adds a context cache key.
 */
class BillingPlansIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

    /** A stored course id that is not a UUID. */
    private static final String MALFORMED_ID = "course-1";

    @Autowired private TestRestTemplate http;
    @Autowired private PlanJpaRepository planJpaRepository;
    @Autowired private PlanCourseJpaRepository planCourseJpaRepository;
    @Autowired private VirtualCourseJpaRepository virtualCourseRepository;
    @Autowired private PhysicalCourseJpaRepository physicalCourseRepository;

    @AfterEach
    void cleanUp() {
        planCourseJpaRepository.deleteAll();
        planJpaRepository.deleteAll();
        virtualCourseRepository.deleteAll();
        physicalCourseRepository.deleteAll();
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

    private void seedVirtualCourse(UUID id, String title, CourseStatus status) {
        Instant now = Instant.now();
        virtualCourseRepository.save(new VirtualCourseJpaEntity(
            id, title, "desc " + title, "descripción larga", UUID.randomUUID(), "https://cdn/img.jpg",
            "tango", "BEGINNER", false, status, now, now
        ));
    }

    private void seedPhysicalCourse(UUID id, String title, boolean active) {
        Instant now = Instant.now();
        var status = active
            ? com.menta.physical.domain.model.CourseStatus.ACTIVE
            : com.menta.physical.domain.model.CourseStatus.INACTIVE;
        physicalCourseRepository.save(new PhysicalCourseJpaEntity(
            id, title, "desc " + title, UUID.randomUUID(), "María García", "TUESDAY",
            LocalTime.of(19, 0), 60, "BEGINNER", 20, status, now, now
        ));
    }

    private void includeCourses(UUID planId, Object... courseIds) {
        for (Object courseId : courseIds) {
            planCourseJpaRepository.save(new PlanCourseJpaEntity(planId, courseId.toString()));
        }
    }

    /**
     * One ACTIVE plan per scenario of the course-name rules, priced so the list returns them in
     * this order: a mixed plan, a plan of courses that are not publicly visible, a plan of
     * unresolvable ids next to a resolvable one, and a plan without courses.
     */
    private Catalog seedCatalog() {
        Catalog catalog = new Catalog(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
        seedVirtualCourse(catalog.virtualId, "Salsa Online", CourseStatus.PUBLISHED);
        seedPhysicalCourse(catalog.physicalId, "Bachata Nivel 1", true);
        seedVirtualCourse(catalog.bothId, "Virtual C", CourseStatus.PUBLISHED);
        seedPhysicalCourse(catalog.bothId, "Fisico C", true);
        seedVirtualCourse(catalog.draftId, "Salsa Draft", CourseStatus.DRAFT);
        seedVirtualCourse(catalog.archivedId, "Vals Archivado", CourseStatus.ARCHIVED);
        seedPhysicalCourse(catalog.inactiveId, "Tango Inactivo", false);
        includeCourses(
            seedPlan("Mixto", new BigDecimal("10000.00"), false, PlanStatus.ACTIVE),
            catalog.virtualId, catalog.physicalId, catalog.bothId
        );
        includeCourses(
            seedPlan("Invisibles", new BigDecimal("20000.00"), false, PlanStatus.ACTIVE),
            catalog.draftId, catalog.archivedId, catalog.inactiveId
        );
        includeCourses(
            seedPlan("Desconocidos", new BigDecimal("30000.00"), false, PlanStatus.ACTIVE),
            catalog.unknownId, MALFORMED_ID, catalog.virtualId
        );
        seedPlan("Sin cursos", new BigDecimal("40000.00"), false, PlanStatus.ACTIVE);
        return catalog;
    }

    /** Ids of the seeded courses; the draft, archived, inactive and unknown ones never resolve. */
    private record Catalog(
        UUID virtualId, UUID physicalId, UUID bothId, UUID draftId, UUID archivedId,
        UUID inactiveId, UUID unknownId
    ) {
    }

    private Map<String, Object> getBody(String path) {
        ResponseEntity<Map> response = http.exchange(path, HttpMethod.GET, null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Map<String, Object>> listedPlansByName() {
        allowRateLimit();
        List<Map<String, Object>> plans =
            (List<Map<String, Object>>) getBody("/api/v1/billing/plans").get("plans");
        return plans.stream()
            .collect(Collectors.toMap(plan -> (String) plan.get("name"), Function.identity()));
    }

    private Map<String, Object> detailedPlan(Map<String, Object> listedPlan) {
        return getBody("/api/v1/billing/plans/" + listedPlan.get("id"));
    }

    private static List<Map<String, Object>> coursesOf(Map<String, Object> plan) {
        return (List<Map<String, Object>>) plan.get("courses");
    }

    /** Each course as {@code id=name} (a null name reads {@code null}), in the response order. */
    private static List<String> idAndName(Map<String, Object> plan) {
        return coursesOf(plan).stream()
            .map(course -> course.get("id") + "=" + course.get("name"))
            .toList();
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
        UUID courseId = UUID.randomUUID();
        seedVirtualCourse(courseId, "Tango Basico", CourseStatus.PUBLISHED);
        includeCourses(id, courseId);

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

    @Test
    void resolvable_courses_show_their_title_with_virtual_first_and_the_plan_course_order() {
        Catalog catalog = seedCatalog();

        Map<String, Object> mixed = listedPlansByName().get("Mixto");

        assertThat(idAndName(mixed)).containsExactly(
            catalog.virtualId + "=Salsa Online",
            catalog.physicalId + "=Bachata Nivel 1",
            catalog.bothId + "=Virtual C"
        );
    }

    @Test
    void courses_that_are_not_publicly_visible_keep_their_id_with_a_null_name() {
        Catalog catalog = seedCatalog();

        Map<String, Object> hidden = listedPlansByName().get("Invisibles");

        assertThat(idAndName(hidden)).containsExactly(
            catalog.draftId + "=null", catalog.archivedId + "=null", catalog.inactiveId + "=null"
        );
    }

    @Test
    void unknown_and_malformed_ids_have_a_null_name_without_affecting_a_resolvable_course() {
        Catalog catalog = seedCatalog();

        Map<String, Object> unresolved = listedPlansByName().get("Desconocidos");

        assertThat(idAndName(unresolved)).containsExactly(
            catalog.unknownId + "=null", MALFORMED_ID + "=null", catalog.virtualId + "=Salsa Online"
        );
    }

    @Test
    void a_plan_without_courses_lists_an_empty_course_list() {
        seedCatalog();

        Map<String, Object> empty = listedPlansByName().get("Sin cursos");

        assertThat(coursesOf(empty)).isEmpty();
    }

    @Test
    void a_course_shared_by_several_plans_has_the_same_title_in_each_of_them() {
        Catalog catalog = seedCatalog();

        Map<String, Map<String, Object>> plans = listedPlansByName();

        String sharedCourse = catalog.virtualId + "=Salsa Online";
        assertThat(idAndName(plans.get("Mixto"))).contains(sharedCourse);
        assertThat(idAndName(plans.get("Desconocidos"))).contains(sharedCourse);
    }

    @Test
    void get_resolves_the_same_names_as_the_list_for_every_kind_of_plan() {
        Catalog catalog = seedCatalog();
        Map<String, Map<String, Object>> plans = listedPlansByName();

        assertThat(idAndName(detailedPlan(plans.get("Mixto")))).containsExactly(
            catalog.virtualId + "=Salsa Online",
            catalog.physicalId + "=Bachata Nivel 1",
            catalog.bothId + "=Virtual C"
        );
        assertThat(idAndName(detailedPlan(plans.get("Invisibles")))).containsExactly(
            catalog.draftId + "=null", catalog.archivedId + "=null", catalog.inactiveId + "=null"
        );
        assertThat(idAndName(detailedPlan(plans.get("Desconocidos")))).containsExactly(
            catalog.unknownId + "=null", MALFORMED_ID + "=null", catalog.virtualId + "=Salsa Online"
        );
        assertThat(coursesOf(detailedPlan(plans.get("Sin cursos")))).isEmpty();
    }

    @Test
    void every_course_item_has_exactly_the_keys_id_and_name_in_both_endpoints() {
        seedCatalog();
        Map<String, Map<String, Object>> plans = listedPlansByName();

        for (String planName : List.of("Mixto", "Invisibles")) {
            List<Map<String, Object>> listed = coursesOf(plans.get(planName));
            List<Map<String, Object>> detailed = coursesOf(detailedPlan(plans.get(planName)));
            assertThat(listed).hasSize(3);
            assertThat(detailed).hasSize(3);
            listed.forEach(course -> assertThat(course).containsOnlyKeys("id", "name"));
            detailed.forEach(course -> assertThat(course).containsOnlyKeys("id", "name"));
        }
    }
}
