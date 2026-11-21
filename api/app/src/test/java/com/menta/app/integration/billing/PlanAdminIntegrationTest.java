package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.menta.app.integration.support.CatalogAccessMocksIntegrationTestBase;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanPaymentMethodJpaRepository;
import com.menta.shared.domain.vo.Email;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * End-to-end coverage for {@code POST /api/v1/admin/billing/plans} (#762) over the real {@code
 * SecurityConfig} filter chain, a real login, MySQL persistence and the real course catalog: an
 * admin creates a plan and the public plans API reads it back.
 *
 * <p>Extends the same base as {@code BillingPlansIntegrationTest}, so both share one Spring
 * context and schema; every row this class writes is removed after each test.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class PlanAdminIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

    private static final String ADMIN_PLANS_URL = "/api/v1/admin/billing/plans";
    private static final String PUBLIC_PLANS_URL = "/api/v1/billing/plans";
    private static final String PASSWORD = "SecurePass123!";
    private static final String UNKNOWN_COURSE_ID = "course-not-in-any-catalog";

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlanRepository planRepository;
    @Autowired private PlanJpaRepository planJpaRepository;
    @Autowired private PlanCourseJpaRepository planCourseJpaRepository;
    @Autowired private PlanPaymentMethodJpaRepository planPaymentMethodJpaRepository;
    @Autowired private VirtualCourseJpaRepository virtualCourseRepository;

    @BeforeEach
    void allowLoginsAndCatalogReads() {
        when(loginRateLimitPort.check(anyString(), anyString()))
            .thenReturn(com.menta.auth.application.port.out.RateLimitDecision.allowed());
        when(billingPlansRateLimitPort.consume(any())).thenReturn(RateLimitDecision.allowed());
    }

    @AfterEach
    void cleanUp() {
        planCourseJpaRepository.deleteAll();
        planPaymentMethodJpaRepository.deleteAll();
        planJpaRepository.deleteAll();
        virtualCourseRepository.deleteAll();
    }

    // --- fixtures -----------------------------------------------------------

    /** Persists an active user of the role and logs them in through the real login endpoint. */
    private String accessTokenOf(Role role) {
        String email = "plans-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.create(Email.of(email), passwordEncoder.encode(PASSWORD), role,
            java.time.Instant.now()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = http.exchange("/api/v1/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", email, "password", PASSWORD), headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("access_token");
    }

    private UUID seedVirtualCourse(String title) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        virtualCourseRepository.save(new VirtualCourseJpaEntity(
            id, title, "desc " + title, "descripción larga", UUID.randomUUID(), "https://cdn/img.jpg",
            "tango", "BEGINNER", false, CourseStatus.PUBLISHED, now, now
        ));
        return id;
    }

    private static Map<String, Object> planBody(String name, String status, List<String> courseIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "Acceso a " + name);
        body.put("price", 15000.50);
        body.put("currency", "ARS");
        body.put("durationDays", 30);
        body.put("featured", true);
        body.put("status", status);
        body.put("termsAndConditions", "Términos de " + name);
        body.put("cancellationPolicy", "Política de " + name);
        body.put("paymentMethods", List.of("MERCADO_PAGO", "BANK_TRANSFER"));
        body.put("courseIds", courseIds);
        return body;
    }

    private ResponseEntity<Map> createPlan(String accessToken, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return http.exchange(ADMIN_PLANS_URL, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private List<String> publiclyListedPlanIds() {
        ResponseEntity<Map> response = http.exchange(PUBLIC_PLANS_URL, HttpMethod.GET, null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((List<Map<String, Object>>) response.getBody().get("plans")).stream()
            .map(plan -> (String) plan.get("id"))
            .toList();
    }

    private ResponseEntity<Map> publicDetail(String planId) {
        return http.exchange(PUBLIC_PLANS_URL + "/" + planId, HttpMethod.GET, null, Map.class);
    }

    // --- scenarios ----------------------------------------------------------

    @Test
    void an_admin_creates_an_active_plan_that_the_public_list_and_detail_show() {
        UUID courseId = seedVirtualCourse("Tango Básico");
        String adminToken = accessTokenOf(Role.ADMIN);

        ResponseEntity<Map> created = createPlan(
            adminToken, planBody("Plan Mensual", "ACTIVE", List.of(courseId.toString(), UNKNOWN_COURSE_ID))
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = created.getBody();
        String planId = (String) body.get("id");
        assertThat(UUID.fromString(planId)).isNotNull();
        assertThat(body).containsOnlyKeys(
            "id", "name", "description", "price", "currency", "durationDays", "featured",
            "termsAndConditions", "cancellationPolicy", "courses"
        );
        assertThat(publiclyListedPlanIds()).containsExactly(planId);
        ResponseEntity<Map> detail = publicDetail(planId);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).isEqualTo(body);
        assertThat(detail.getBody().get("name")).isEqualTo("Plan Mensual");
        assertThat(detail.getBody().get("price")).isEqualTo(15000.50);
        assertThat(detail.getBody().get("currency")).isEqualTo("ARS");
        assertThat(detail.getBody().get("durationDays")).isEqualTo(30);
        assertThat(detail.getBody().get("featured")).isEqualTo(true);
        assertThat(detail.getBody().get("termsAndConditions")).isEqualTo("Términos de Plan Mensual");
        assertThat(detail.getBody().get("cancellationPolicy")).isEqualTo("Política de Plan Mensual");
        assertThat((List<Map<String, Object>>) detail.getBody().get("courses"))
            .extracting(course -> course.get("id") + "=" + course.get("name"))
            .containsExactly(courseId + "=Tango Básico", UNKNOWN_COURSE_ID + "=null");
    }

    @Test
    void the_payment_methods_and_courses_of_a_created_plan_round_trip_through_persistence() {
        String adminToken = accessTokenOf(Role.ADMIN);

        ResponseEntity<Map> created = createPlan(
            adminToken, planBody("Plan Trimestral", "ACTIVE", List.of("course-b", "course-a"))
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Plan stored = planRepository.findById(PlanId.of((String) created.getBody().get("id"))).orElseThrow();
        assertThat(stored.getPaymentMethods())
            .containsExactlyInAnyOrder(PaymentMethod.MERCADO_PAGO, PaymentMethod.BANK_TRANSFER);
        assertThat(stored.courseIds()).containsExactly("course-b", "course-a");
        assertThat(stored.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(stored.getPrice().getAmount()).isEqualByComparingTo("15000.50");
    }

    @Test
    void an_inactive_plan_is_created_but_never_shown_publicly() {
        String adminToken = accessTokenOf(Role.ADMIN);

        ResponseEntity<Map> created = createPlan(adminToken, planBody("Plan Oculto", "INACTIVE", List.of()));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String planId = (String) created.getBody().get("id");
        assertThat(planRepository.findById(PlanId.of(planId))).isPresent();
        assertThat(publiclyListedPlanIds()).doesNotContain(planId);
        assertThat(publicDetail(planId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void an_invalid_payload_is_rejected_with_400_and_creates_nothing() {
        final String adminToken = accessTokenOf(Role.ADMIN);
        Map<String, Object> zeroPrice = planBody("Plan Gratis", "ACTIVE", List.of());
        zeroPrice.put("price", 0);
        Map<String, Object> zeroDuration = planBody("Plan Cero", "ACTIVE", List.of());
        zeroDuration.put("durationDays", 0);
        Map<String, Object> noMethods = planBody("Plan Sin Medios", "ACTIVE", List.of());
        noMethods.put("paymentMethods", List.of());
        Map<String, Object> unknownMethod = planBody("Plan Efectivo", "ACTIVE", List.of());
        unknownMethod.put("paymentMethods", List.of("CASH"));
        Map<String, Object> blankCourse = planBody("Plan Curso Vacío", "ACTIVE", List.of(" "));
        Map<String, Object> incomplete = planBody("Plan Incompleto", "ACTIVE", List.of());
        incomplete.remove("name");

        for (Map<String, Object> body : List.of(zeroPrice, zeroDuration, noMethods, unknownMethod, blankCourse,
            incomplete)) {
            ResponseEntity<Map> response = createPlan(adminToken, body);

            assertThat(response.getStatusCode()).as("%s", body).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
        }
        assertThat(planJpaRepository.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STUDENT", "INSTRUCTOR", "RECEPTIONIST"})
    void a_non_admin_is_forbidden_and_creates_nothing(Role role) {
        String token = accessTokenOf(role);

        ResponseEntity<Map> response = createPlan(token, planBody("Plan Ajeno", "ACTIVE", List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(planJpaRepository.count()).isZero();
    }

    @Test
    void an_anonymous_caller_is_rejected_with_401_and_creates_nothing() {
        ResponseEntity<Map> response = createPlan(null, planBody("Plan Anónimo", "ACTIVE", List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(planJpaRepository.count()).isZero();
    }
}
