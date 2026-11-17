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
import com.menta.virtual.infrastructure.persistence.entity.LessonProgressJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.LessonProgressJpaRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * #765 end to end against real MySQL: the real security chain, the real JPQL read, the real
 * {@code CourseProgressAccessPolicy} and the real Billing {@code VirtualCourseEntitlementService}
 * (no cross-module port is mocked). Shares the Spring context of {@code
 * VirtualLessonAccessIntegrationTest} through {@link CatalogAccessMocksIntegrationTestBase}.
 */
class InProgressCoursesIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

    private static final String URL = "/api/v1/virtual/courses/me/in-progress";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private VirtualCourseJpaRepository courseRepository;
    @Autowired private VirtualModuleJpaRepository moduleRepository;
    @Autowired private VirtualLessonJpaRepository lessonRepository;
    @Autowired private LessonProgressJpaRepository progressRepository;
    @Autowired private PlanJpaRepository planRepository;
    @Autowired private PlanCourseJpaRepository planCourseRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private SubscriptionJpaRepository subscriptionRepository;
    @Autowired private SubscriptionCourseJpaRepository subscriptionCourseRepository;

    @AfterEach
    void cleanUp() {
        progressRepository.deleteAll();
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

    private UUID seedCourse(String title) {
        UUID id = UUID.randomUUID();
        courseRepository.save(new VirtualCourseJpaEntity(
            id, title, "desc", "descripción larga", UUID.randomUUID(), "https://cdn/img.jpg",
            "tango", "BEGINNER", true, CourseStatus.PUBLISHED, NOW, NOW
        ));
        return id;
    }

    /** Seeds one module with the given lessons, in display order; returns their ids. */
    private List<UUID> seedLessons(UUID courseId, String... titles) {
        UUID moduleId = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo 1", false, 0));
        List<UUID> ids = new java.util.ArrayList<>();
        for (int order = 0; order < titles.length; order++) {
            UUID lessonId = UUID.randomUUID();
            lessonRepository.save(new VirtualLessonJpaEntity(
                lessonId, moduleId, courseId, titles[order], "desc", "video-" + order, 10, false, order
            ));
            ids.add(lessonId);
        }
        return ids;
    }

    private void seedProgress(
        UUID userId, UUID courseId, UUID lessonId, int position, boolean completed, Instant positionUpdatedAt
    ) {
        progressRepository.save(new LessonProgressJpaEntity(
            UUID.randomUUID(), userId, lessonId, courseId, position, completed, completed ? NOW : null,
            NOW, positionUpdatedAt, NOW
        ));
    }

    private UUID seedPlan(UUID... courseIds) {
        UUID planId = UUID.randomUUID();
        planRepository.save(new PlanJpaEntity(
            planId, "Plan Virtual", "Acceso virtual", new BigDecimal("100.00"), "ARS", 30,
            false, PlanStatus.ACTIVE, "Términos", "Política", NOW, NOW
        ));
        for (UUID courseId : courseIds) {
            planCourseRepository.save(new PlanCourseJpaEntity(planId, courseId.toString()));
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

    /** Same direct snapshot seeding as {@code VirtualLessonAccessIntegrationTest}. */
    private void seedSubscription(UUID userId, UUID planId, String status, Instant endDate, UUID... courseIds) {
        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new PaymentJpaEntity(
            paymentId, userId, "mp-" + paymentId, new BigDecimal("100.00"), "ARS",
            "SUB-" + paymentId, "merchant-1", "MERCADO_PAGO", "VIRTUAL", planId.toString(), "COMPLETED",
            null, NOW, NOW
        ));
        UUID subscriptionId = UUID.randomUUID();
        subscriptionRepository.save(new SubscriptionJpaEntity(
            subscriptionId, paymentId, userId, planId, "idem-" + subscriptionId, null,
            status, "ASSIGNED", NOW.minus(40, ChronoUnit.DAYS), endDate, null, null, NOW,
            null, null, null, "PAID", null, null, null, null, 0L
        ));
        for (UUID courseId : courseIds) {
            subscriptionCourseRepository.save(new SubscriptionCourseJpaEntity(subscriptionId, courseId.toString()));
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

    @SuppressWarnings("rawtypes")
    private ResponseEntity<List> list(String query, HttpHeaders headers) {
        return http.exchange(URL + query, HttpMethod.GET, new HttpEntity<>(headers), List.class);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> problem(String query, HttpHeaders headers) {
        return http.exchange(URL + query, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    /** The scenario of the issue: two in progress, one completed, one without access, one untouched. */
    private record Scenario(
        UUID student, UUID recent, UUID older, List<UUID> olderLessons, UUID completed, UUID lostAccess
    ) {
    }

    private Scenario seedScenario() {
        UUID student = seedStudent();
        UUID older = seedCourse("Tango Básico");
        final List<UUID> olderLessons = seedLessons(older, "Abrazo", "Postura");
        UUID recent = seedCourse("Salsa");
        final List<UUID> recentLessons = seedLessons(recent, "Básico", "Giros", "Figuras");
        UUID completed = seedCourse("Milonga");
        final List<UUID> completedLessons = seedLessons(completed, "Única");
        UUID lostAccess = seedCourse("Vals");
        final List<UUID> lostAccessLessons = seedLessons(lostAccess, "Primera", "Segunda");
        UUID untouched = seedCourse("Zamba");
        seedLessons(untouched, "Sin empezar");

        UUID planId = seedPlan(older, recent, completed, lostAccess, untouched);
        seedSubscription(
            student, planId, "ACTIVE", NOW.plus(30, ChronoUnit.DAYS), older, recent, completed, untouched
        );
        seedSubscription(student, planId, "EXPIRED", NOW.minus(1, ChronoUnit.DAYS), lostAccess);

        seedProgress(student, older, olderLessons.get(0), 0, true, NOW.minus(3, ChronoUnit.HOURS));
        seedProgress(student, older, olderLessons.get(1), 40, false, NOW.minus(2, ChronoUnit.HOURS));
        seedProgress(student, recent, recentLessons.get(1), 95, false, NOW.minus(1, ChronoUnit.HOURS));
        seedProgress(student, completed, completedLessons.get(0), 600, true, NOW.minus(10, ChronoUnit.MINUTES));
        // The most recent activity of all, in a course whose subscription expired: filtered, not limited.
        seedProgress(student, lostAccess, lostAccessLessons.get(0), 30, false, NOW.minus(5, ChronoUnit.MINUTES));
        return new Scenario(student, recent, older, olderLessons, completed, lostAccess);
    }

    // --- scenarios ------------------------------------------------------

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void lists_only_accessible_unfinished_courses_most_recent_first_with_titles_and_resume_lesson() {
        Scenario scenario = seedScenario();

        ResponseEntity<List> response = list("", authHeadersFor(scenario.student()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> courses = response.getBody();
        assertThat(courses).extracting(course -> course.get("courseId"))
            .containsExactly(scenario.recent().toString(), scenario.older().toString());
        Map<String, Object> recent = courses.get(0);
        assertThat(recent).containsOnlyKeys(
            "courseId", "courseTitle", "completedLessons", "totalLessons", "percentage", "resumeLesson"
        );
        assertThat(recent).containsEntry("courseTitle", "Salsa")
            .containsEntry("completedLessons", 0).containsEntry("totalLessons", 3).containsEntry("percentage", 0);
        Map<String, Object> older = courses.get(1);
        assertThat(older).containsEntry("courseTitle", "Tango Básico")
            .containsEntry("completedLessons", 1).containsEntry("totalLessons", 2).containsEntry("percentage", 50);
        Map<String, Object> resume = (Map<String, Object>) older.get("resumeLesson");
        assertThat(resume).containsOnlyKeys("lessonId", "moduleId", "lessonTitle", "positionSeconds");
        assertThat(resume).containsEntry("lessonId", scenario.olderLessons().get(1).toString())
            .containsEntry("lessonTitle", "Postura").containsEntry("positionSeconds", 40);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void the_resume_lesson_and_percentage_match_the_per_course_progress_endpoint() {
        Scenario scenario = seedScenario();
        HttpHeaders auth = authHeadersFor(scenario.student());

        Map<String, Object> listed = (Map<String, Object>) list("", auth).getBody().get(1);
        ResponseEntity<Map> perCourse = http.exchange(
            "/api/v1/virtual/courses/" + scenario.older() + "/progress", HttpMethod.GET, new HttpEntity<>(auth),
            Map.class
        );

        assertThat(perCourse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.get("percentage")).isEqualTo(perCourse.getBody().get("percentage"));
        assertThat(listed.get("completedLessons")).isEqualTo(perCourse.getBody().get("completedLessons"));
        assertThat(((Map<String, Object>) listed.get("resumeLesson")).get("lessonId"))
            .isEqualTo(((Map<String, Object>) perCourse.getBody().get("resumeLesson")).get("lessonId"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void another_students_progress_never_leaks_into_the_listing() {
        Scenario scenario = seedScenario();
        UUID other = seedStudent();
        UUID otherPlan = seedPlan(scenario.older());
        seedSubscription(other, otherPlan, "ACTIVE", NOW.plus(30, ChronoUnit.DAYS), scenario.older());
        seedProgress(other, scenario.older(), scenario.olderLessons().get(0), 7, false, NOW);

        List<Map<String, Object>> mine = list("", authHeadersFor(scenario.student())).getBody();
        List<Map<String, Object>> theirs = list("", authHeadersFor(other)).getBody();

        assertThat(mine).extracting(course -> course.get("courseId"))
            .containsExactly(scenario.recent().toString(), scenario.older().toString());
        assertThat(((Map<String, Object>) mine.get(1).get("resumeLesson")).get("positionSeconds")).isEqualTo(40);
        assertThat(theirs).extracting(course -> course.get("courseId"))
            .containsExactly(scenario.older().toString());
        assertThat(theirs.get(0)).containsEntry("completedLessons", 0);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void the_limit_keeps_the_most_recent_accessible_courses() {
        Scenario scenario = seedScenario();

        ResponseEntity<List> response = list("?limit=1", authHeadersFor(scenario.student()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) response.getBody()).extracting(course -> course.get("courseId"))
            .containsExactly(scenario.recent().toString());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void a_student_without_progress_gets_an_empty_array() {
        UUID student = seedStudent();

        ResponseEntity<List> response = list("", authHeadersFor(student));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void an_invalid_limit_is_a_400_problem_with_a_stable_code() {
        HttpHeaders auth = authHeadersFor(seedStudent());

        for (String limit : List.of("0", "51", "diez")) {
            ResponseEntity<Map> response = problem("?limit=" + limit, auth);

            assertThat(response.getStatusCode()).as("limit=%s", limit).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(response.getBody()).containsEntry("code", "INVALID_REQUEST");
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    void an_anonymous_caller_is_rejected_with_401() {
        ResponseEntity<Map> response = problem("", new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("code", "AUTHENTICATION_REQUIRED");
    }
}
