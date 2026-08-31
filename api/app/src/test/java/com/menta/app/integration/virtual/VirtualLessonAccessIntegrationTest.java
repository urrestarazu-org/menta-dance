package com.menta.app.integration.virtual;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.shared.domain.vo.Email;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cross-module, real-Spring-context coverage for issue #56 (TASK-006):
 * proves the lesson-access boundary end to end through the actual HTTP
 * surface, the real {@code LessonAccessPolicy}, and the real Billing
 * {@code VirtualCourseEntitlementService} — no cross-module port is
 * mocked. Anchors the CURRENT contract in {@code specs/virtual/spec.md}
 * (including D7: a course absent from every plan denies by default, it
 * is no longer a public grant).
 *
 * <p>{@code app_should_not_own_virtual_lesson_access_policy} in
 * {@link com.menta.app.ArchitectureTest} already guards the module
 * boundary at the class-dependency level; this class supplies the
 * behavioral regression TASK-006 additionally requires.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class VirtualLessonAccessIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private VirtualCourseJpaRepository courseRepository;
    @Autowired private VirtualModuleJpaRepository moduleRepository;
    @Autowired private VirtualLessonJpaRepository lessonRepository;
    @Autowired private PlanJpaRepository planRepository;
    @Autowired private PlanCourseJpaRepository planCourseRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private SubscriptionJpaRepository subscriptionRepository;
    @Autowired private SubscriptionCourseJpaRepository subscriptionCourseRepository;

    // Every Redis-backed port needs a mock: integration-test excludes
    // RedisAutoConfiguration and this is one shared Spring context. Mirrors
    // VirtualCourseCatalogIntegrationTest's / SubscriptionCheckoutIntegrationTest's exact mock set.
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    // US-PHYSICAL-001: ProcessPhysicalCheckInUseCaseImpl needs a RedisTemplate
    // its bean factory would otherwise fail to resolve in this Redis-less context.
    @MockBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;

    @AfterEach
    void cleanUp() {
        subscriptionCourseRepository.deleteAll();
        subscriptionRepository.deleteAll();
        paymentRepository.deleteAll();
        planCourseRepository.deleteAll();
        planRepository.deleteAll();
        lessonRepository.deleteAll();
        moduleRepository.deleteAll();
        courseRepository.deleteAll();
    }

    // --- fixtures -------------------------------------------------------

    private UUID seedCourse() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new VirtualCourseJpaEntity(
            id, "Tango Avanzado", "desc", "descripción larga", UUID.randomUUID(), "https://cdn/img.jpg",
            "tango", "ADVANCED", false, CourseStatus.PUBLISHED, now, now
        ));
        return id;
    }

    private UUID seedProtectedLesson(UUID courseId) {
        UUID moduleId = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo premium", false, 0));
        UUID lessonId = UUID.randomUUID();
        lessonRepository.save(new VirtualLessonJpaEntity(
            lessonId, moduleId, courseId, "Lección premium", "desc", "video-premium", 10, false, 0
        ));
        return lessonId;
    }

    private UUID seedPlan(String... courseIds) {
        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(new PlanJpaEntity(
            planId, "Plan Virtual", "Acceso virtual", new BigDecimal("100.00"), "ARS", 30,
            false, PlanStatus.ACTIVE, "Términos", "Política", now, now
        ));
        for (String courseId : courseIds) {
            planCourseRepository.save(new PlanCourseJpaEntity(planId, courseId));
        }
        return planId;
    }

    private UUID seedStudent() {
        User user = User.create(
            Email.of("student-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", Role.STUDENT
        );
        userRepository.save(user);
        return user.getId().getValue();
    }

    /**
     * Seeds a subscription snapshot directly (bypassing checkout/webhook),
     * satisfying the {@code fk_billing_subscriptions_payment}/{@code _plan}
     * constraints with a matching {@code COMPLETED} payment row.
     */
    private void seedSubscription(
        UUID userId, UUID planId, String status, Instant endDate, String... snapshotCourseIds
    ) {
        Instant now = Instant.now();
        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new PaymentJpaEntity(
            paymentId, userId, "mp-" + paymentId, new BigDecimal("100.00"), "ARS",
            "SUB-" + paymentId, "merchant-1", "VIRTUAL", planId.toString(), "COMPLETED",
            null, now, now
        ));
        UUID subscriptionId = UUID.randomUUID();
        subscriptionRepository.save(new SubscriptionJpaEntity(
            subscriptionId, paymentId, userId, planId, "idem-" + subscriptionId, null,
            status, "ASSIGNED", now.minus(1, ChronoUnit.DAYS), endDate, null, null, now,
            null, null, null
        ));
        for (String courseId : snapshotCourseIds) {
            subscriptionCourseRepository.save(new SubscriptionCourseJpaEntity(subscriptionId, courseId));
        }
    }

    private HttpHeaders authHeadersFor(UUID userId) {
        User user = new User(
            UserId.of(userId), Email.of("token@example.com"), "hash", Role.STUDENT, UserStatus.ACTIVE,
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessTokenIssuer.issue(user).token());
        return headers;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> getLessonDetail(UUID lessonId, HttpHeaders headers) {
        return http.exchange(
            "/api/v1/virtual/lessons/" + lessonId, HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> getLessonStream(UUID lessonId, HttpHeaders headers) {
        return http.exchange(
            "/api/v1/virtual/lessons/" + lessonId + "/stream", HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );
    }

    private static void assertProblemJsonForbidden(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("code", "LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED");
        // No-leak: neither detail nor stream denial may expose a media capability.
        assertThat(response.getBody().toString()).doesNotContain("videoId").doesNotContain("streamUrl");
    }

    // --- Escenario 1: planned course + active subscription grants -----

    @Test
    void active_subscription_grants_detail_and_stream_for_a_planned_course() {
        UUID courseId = seedCourse();
        UUID lessonId = seedProtectedLesson(courseId);
        UUID planId = seedPlan(courseId.toString());
        UUID userId = seedStudent();
        seedSubscription(userId, planId, "ACTIVE", Instant.now().plus(30, ChronoUnit.DAYS), courseId.toString());
        HttpHeaders auth = authHeadersFor(userId);

        ResponseEntity<Map> detail = getLessonDetail(lessonId, auth);
        ResponseEntity<Map> stream = getLessonStream(lessonId, auth);

        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) detail.getBody().get("lesson")).get("videoId")).isEqualTo("video-premium");
        assertThat(stream.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) stream.getBody().get("stream")).get("url")).isNotNull();
    }

    // --- Escenario 2: protected lesson without entitlement is denied, no leak ---

    @Test
    void protected_lesson_without_entitlement_is_denied_for_anonymous_and_authenticated_callers() {
        UUID courseId = seedCourse();
        UUID lessonId = seedProtectedLesson(courseId);
        seedPlan(courseId.toString());
        UUID userId = seedStudent();

        ResponseEntity<Map> anonymousDetail = getLessonDetail(lessonId, new HttpHeaders());
        ResponseEntity<Map> anonymousStream = getLessonStream(lessonId, new HttpHeaders());
        ResponseEntity<Map> authenticatedDetail = getLessonDetail(lessonId, authHeadersFor(userId));

        assertProblemJsonForbidden(anonymousDetail);
        assertProblemJsonForbidden(anonymousStream);
        assertProblemJsonForbidden(authenticatedDetail);
    }

    // --- Escenario 3: frozen snapshot survives the course leaving the live plan ---

    @Test
    void a_frozen_entitlement_snapshot_survives_the_course_leaving_the_live_plan() {
        UUID courseId = seedCourse();
        UUID lessonId = seedProtectedLesson(courseId);
        UUID planId = seedPlan(courseId.toString());
        UUID userId = seedStudent();
        seedSubscription(userId, planId, "ACTIVE", Instant.now().plus(30, ChronoUnit.DAYS), courseId.toString());
        HttpHeaders auth = authHeadersFor(userId);

        // The administrator removes the course from the live plan definition after purchase.
        planCourseRepository.deleteAll(planCourseRepository.findByPlanId(planId));

        ResponseEntity<Map> detail = getLessonDetail(lessonId, auth);
        ResponseEntity<Map> stream = getLessonStream(lessonId, auth);

        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stream.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- Escenario 4: cancellation before end date still grants access ---

    @Test
    void cancellation_before_end_date_still_grants_access() {
        UUID courseId = seedCourse();
        UUID lessonId = seedProtectedLesson(courseId);
        UUID planId = seedPlan(courseId.toString());
        UUID userId = seedStudent();
        seedSubscription(userId, planId, "CANCELLED", Instant.now().plus(5, ChronoUnit.DAYS), courseId.toString());
        HttpHeaders auth = authHeadersFor(userId);

        ResponseEntity<Map> detail = getLessonDetail(lessonId, auth);
        ResponseEntity<Map> stream = getLessonStream(lessonId, auth);

        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stream.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- Escenario 5: expired subscription denies ----------------------

    @Test
    void an_expired_subscription_denies_access() {
        UUID courseId = seedCourse();
        UUID lessonId = seedProtectedLesson(courseId);
        UUID planId = seedPlan(courseId.toString());
        UUID userId = seedStudent();
        seedSubscription(userId, planId, "EXPIRED", Instant.now().minus(1, ChronoUnit.DAYS), courseId.toString());
        HttpHeaders auth = authHeadersFor(userId);

        ResponseEntity<Map> detail = getLessonDetail(lessonId, auth);
        ResponseEntity<Map> stream = getLessonStream(lessonId, auth);

        assertProblemJsonForbidden(detail);
        assertProblemJsonForbidden(stream);
    }

    // --- Escenario 6 (D7): a course never associated with any plan denies by default ---

    @Test
    void a_course_absent_from_every_plan_denies_by_default_for_anonymous_and_authenticated_callers() {
        UUID courseId = seedCourse();
        UUID lessonId = seedProtectedLesson(courseId);
        // No plan is ever created referencing this course — D7 supersedes the
        // prior "unplanned course is public" semantics from the original proposal.
        UUID userId = seedStudent();

        ResponseEntity<Map> anonymousDetail = getLessonDetail(lessonId, new HttpHeaders());
        ResponseEntity<Map> authenticatedDetail = getLessonDetail(lessonId, authHeadersFor(userId));
        ResponseEntity<Map> anonymousStream = getLessonStream(lessonId, new HttpHeaders());

        assertProblemJsonForbidden(anonymousDetail);
        assertProblemJsonForbidden(authenticatedDetail);
        assertProblemJsonForbidden(anonymousStream);
    }
}
