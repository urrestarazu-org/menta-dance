package com.menta.app.integration.virtual;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.app.integration.support.CatalogAccessMocksIntegrationTestBase;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
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
import com.menta.shared.domain.vo.Email;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseAuditJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseAuditJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@code DELETE /api/v1/admin/virtual/courses/{courseId}} of a course that Billing references (#856)
 * against real MySQL, through the real {@code SecurityConfig} chain, the real transactional use case and
 * the real Billing adapter behind the shared {@code VirtualCourseReferencePort}.
 *
 * <p>Billing keeps its references to a virtual course by value, with no foreign key, so the database
 * never stops the delete: only the port does. Any reference counts, including historical ones (a plan
 * that no longer sells the course, a subscription that already expired).</p>
 */
class VirtualCourseInUseDeletionIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

    private static final String COURSES_URL = "/api/v1/admin/virtual/courses/";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private VirtualCourseJpaRepository courseRepository;
    @Autowired private VirtualModuleJpaRepository moduleRepository;
    @Autowired private VirtualLessonJpaRepository lessonRepository;
    @Autowired private VirtualCourseAuditJpaRepository auditRepository;
    @Autowired private PlanJpaRepository planRepository;
    @Autowired private PlanCourseJpaRepository planCourseRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private SubscriptionJpaRepository subscriptionRepository;
    @Autowired private SubscriptionCourseJpaRepository subscriptionCourseRepository;

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
        auditRepository.deleteAll();
    }

    // --- fixtures -------------------------------------------------------

    private UUID seedAdmin() {
        User user = User.create(Email.of("admin-" + UUID.randomUUID() + "@example.com"), "hash", Role.ADMIN, NOW);
        userRepository.save(user);
        return user.getId().getValue();
    }

    private HttpHeaders headersFor(UUID userId) {
        User user = new User(
            UserId.of(userId), Email.of("token@example.com"), "hash", Role.ADMIN, UserStatus.ACTIVE, NOW, NOW
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessTokenIssuer.issue(user).token());
        return headers;
    }

    private UUID seedCourse(UUID professorId, CourseStatus status) {
        UUID id = UUID.randomUUID();
        courseRepository.save(new VirtualCourseJpaEntity(
            id, "Tango Básico", "corta", "larga", professorId, "https://cdn/img.jpg", "tango", "BEGINNER",
            false, status, NOW, NOW
        ));
        return id;
    }

    /** Seeds one module and one lesson with video in the course; returns the lesson id. */
    private UUID seedCourseContent(UUID courseId) {
        UUID moduleId = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo 1", false, 0));
        UUID lessonId = UUID.randomUUID();
        lessonRepository.save(new VirtualLessonJpaEntity(
            lessonId, moduleId, courseId, "Lección 1", "d", "video-0", 10, false, 0
        ));
        return lessonId;
    }

    private UUID seedPlan(PlanStatus status, String... courseIds) {
        UUID planId = UUID.randomUUID();
        planRepository.save(new PlanJpaEntity(
            planId, "Plan Virtual", "Acceso virtual", new BigDecimal("100.00"), "ARS", 30,
            false, status, "Términos", "Política", NOW, NOW
        ));
        for (String courseId : courseIds) {
            planCourseRepository.save(new PlanCourseJpaEntity(planId, courseId));
        }
        return planId;
    }

    /** Seeds an already expired subscription whose frozen snapshot holds the courses. */
    private void seedExpiredSubscription(UUID planId, String... snapshotCourseIds) {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new PaymentJpaEntity(
            paymentId, userId, "mp-" + paymentId, new BigDecimal("100.00"), "ARS",
            "SUB-" + paymentId, "merchant-1", "MERCADO_PAGO", "VIRTUAL", planId.toString(), "COMPLETED",
            null, NOW, NOW
        ));
        UUID subscriptionId = UUID.randomUUID();
        subscriptionRepository.save(new SubscriptionJpaEntity(
            subscriptionId, paymentId, userId, planId, "idem-" + subscriptionId, null,
            "EXPIRED", "ASSIGNED", NOW.minus(60, ChronoUnit.DAYS), NOW.minus(30, ChronoUnit.DAYS), null, null,
            NOW.minus(60, ChronoUnit.DAYS), null, null, null, "PAID", null, null, null, null, 0L
        ));
        for (String courseId : snapshotCourseIds) {
            subscriptionCourseRepository.save(new SubscriptionCourseJpaEntity(subscriptionId, courseId));
        }
    }

    private ResponseEntity<Map> delete(UUID courseId, HttpHeaders headers) {
        return http.exchange(COURSES_URL + courseId, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
    }

    private List<VirtualCourseAuditJpaEntity> deleteAuditRows() {
        return auditRepository.findAll().stream().filter(row -> "DELETE_COURSE".equals(row.getAction())).toList();
    }

    private void assertRejectedAsInUse(ResponseEntity<Map> response, UUID courseId, UUID lessonId) {
        assertThat(response.getStatusCode()).as("body: %s", response.getBody()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_IN_USE");
        assertThat(response.getBody().get("detail")).isEqualTo(
            "El curso no se puede borrar porque está incluido en un plan o en una suscripción."
        );
        assertThat(courseRepository.findById(courseId)).isPresent();
        assertThat(moduleRepository.findAll()).hasSize(1);
        assertThat(lessonRepository.findById(lessonId)).isPresent();
        assertThat(deleteAuditRows()).isEmpty();
    }

    // --- tests ----------------------------------------------------------

    @Test
    void a_sold_and_unpublished_course_in_a_plan_is_rejected_with_409_and_nothing_is_deleted() {
        UUID adminId = seedAdmin();
        HttpHeaders headers = headersFor(adminId);
        UUID courseId = seedCourse(adminId, CourseStatus.PUBLISHED);
        UUID lessonId = seedCourseContent(courseId);
        seedPlan(PlanStatus.ACTIVE, courseId.toString());
        assertThat(http.exchange(
            COURSES_URL + courseId + "/unpublish", HttpMethod.PUT, new HttpEntity<>(headers), Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertRejectedAsInUse(delete(courseId, headers), courseId, lessonId);
    }

    @Test
    void a_course_only_in_an_inactive_plan_is_still_in_use() {
        UUID adminId = seedAdmin();
        UUID courseId = seedCourse(adminId, CourseStatus.DRAFT);
        UUID lessonId = seedCourseContent(courseId);
        seedPlan(PlanStatus.INACTIVE, courseId.toString());

        assertRejectedAsInUse(delete(courseId, headersFor(adminId)), courseId, lessonId);
    }

    @Test
    void a_course_only_in_the_snapshot_of_an_expired_subscription_is_still_in_use() {
        UUID adminId = seedAdmin();
        UUID courseId = seedCourse(adminId, CourseStatus.DRAFT);
        UUID lessonId = seedCourseContent(courseId);
        UUID planWithoutTheCourse = seedPlan(PlanStatus.INACTIVE);
        seedExpiredSubscription(planWithoutTheCourse, courseId.toString());

        assertRejectedAsInUse(delete(courseId, headersFor(adminId)), courseId, lessonId);
    }

    @Test
    void a_published_course_in_a_plan_keeps_answering_course_not_draft() {
        UUID adminId = seedAdmin();
        UUID courseId = seedCourse(adminId, CourseStatus.PUBLISHED);
        seedPlan(PlanStatus.ACTIVE, courseId.toString());

        ResponseEntity<Map> response = delete(courseId, headersFor(adminId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_DRAFT");
    }

    @Test
    void a_draft_course_that_only_other_courses_share_a_plan_with_is_deleted() {
        UUID adminId = seedAdmin();
        UUID soldCourseId = seedCourse(adminId, CourseStatus.PUBLISHED);
        seedPlan(PlanStatus.ACTIVE, soldCourseId.toString());
        UUID draftId = seedCourse(adminId, CourseStatus.DRAFT);
        UUID draftLessonId = seedCourseContent(draftId);

        ResponseEntity<Map> response = delete(draftId, headersFor(adminId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(courseRepository.findById(draftId)).isEmpty();
        assertThat(lessonRepository.findById(draftLessonId)).isEmpty();
        assertThat(courseRepository.findById(soldCourseId)).isPresent();
        assertThat(deleteAuditRows()).hasSize(1);
    }
}
