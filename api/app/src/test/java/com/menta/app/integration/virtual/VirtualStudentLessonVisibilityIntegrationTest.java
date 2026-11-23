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
 * Full-stack coverage of #851: a lesson without a video (null or blank {@code video_id}) does not exist for
 * students and the public surface. It is not listed, not counted, not reachable by id (404
 * {@code LESSON_NOT_FOUND}, even when it is premium and the caller has no subscription) and it is not part of
 * the progress denominator; the management endpoints keep showing it.
 */
class VirtualStudentLessonVisibilityIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

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

    /** A published course with two modules: one visible lesson, three lessons without video. */
    private record Scenario(UUID courseId, UUID visible, UUID freeNoVideo, UUID premiumNoVideo, UUID blankVideo) {
    }

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

    private Scenario seedScenario() {
        UUID courseId = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new VirtualCourseJpaEntity(
            courseId, "Tango Avanzado", "desc", "descripción larga", UUID.randomUUID(), "https://cdn/img.jpg",
            "tango", "ADVANCED", false, CourseStatus.PUBLISHED, now, now
        ));
        UUID withVideoModule = seedModule(courseId, 0);
        UUID onlyStubsModule = seedModule(courseId, 1);
        return new Scenario(
            courseId,
            seedLesson(courseId, withVideoModule, "video-1", 10, false, 0),
            seedLesson(courseId, withVideoModule, null, 20, true, 1),
            seedLesson(courseId, withVideoModule, "", 30, false, 2),
            seedLesson(courseId, onlyStubsModule, "   ", 40, true, 0)
        );
    }

    private UUID seedModule(UUID courseId, int order) {
        UUID moduleId = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo " + order, false, order));
        return moduleId;
    }

    private UUID seedLesson(UUID courseId, UUID moduleId, String videoId, int minutes, boolean free, int order) {
        UUID lessonId = UUID.randomUUID();
        lessonRepository.save(new VirtualLessonJpaEntity(
            lessonId, moduleId, courseId, "Lección " + order, "desc", videoId, minutes, free, order
        ));
        return lessonId;
    }

    private UUID seedUser(Role role) {
        User user = User.create(
            Email.of(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", role,
            Instant.now()
        );
        userRepository.save(user);
        return user.getId().getValue();
    }

    private HttpHeaders authHeadersFor(UUID userId, Role role) {
        User user = new User(
            UserId.of(userId), Email.of("token@example.com"), "hash", role, UserStatus.ACTIVE,
            Instant.now(), Instant.now()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessTokenIssuer.issue(user).token());
        return headers;
    }

    /** A student with a current subscription whose snapshot includes the course. */
    private HttpHeaders entitledStudentFor(UUID courseId) {
        Instant now = Instant.now();
        UUID planId = UUID.randomUUID();
        planRepository.save(new PlanJpaEntity(
            planId, "Plan Virtual", "Acceso virtual", new BigDecimal("100.00"), "ARS", 30,
            false, PlanStatus.ACTIVE, "Términos", "Política", now, now
        ));
        planCourseRepository.save(new PlanCourseJpaEntity(planId, courseId.toString()));
        UUID userId = seedUser(Role.STUDENT);
        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new PaymentJpaEntity(
            paymentId, userId, "mp-" + paymentId, new BigDecimal("100.00"), "ARS",
            "SUB-" + paymentId, "merchant-1", "MERCADO_PAGO", "VIRTUAL", planId.toString(), "COMPLETED",
            null, now, now
        ));
        UUID subscriptionId = UUID.randomUUID();
        subscriptionRepository.save(new SubscriptionJpaEntity(
            subscriptionId, paymentId, userId, planId, "idem-" + subscriptionId, null,
            "ACTIVE", "ASSIGNED", now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), null, null, now,
            null, null, null, "PAID", null, null, null, null, 0L
        ));
        subscriptionCourseRepository.save(new SubscriptionCourseJpaEntity(subscriptionId, courseId.toString()));
        return authHeadersFor(userId, Role.STUDENT);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> call(HttpMethod method, String url, HttpHeaders headers, Object body) {
        return http.exchange(url, method, new HttpEntity<>(body, headers), Map.class);
    }

    private static void assertLessonNotFound(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("code", "LESSON_NOT_FOUND");
        assertThat(response.getBody().toString()).doesNotContain("videoId").doesNotContain("desc");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @Test
    void the_public_course_detail_lists_only_the_lessons_with_video_and_hides_the_module_left_empty() {
        Scenario scenario = seedScenario();

        ResponseEntity<Map> response =
            call(HttpMethod.GET, "/api/v1/catalog/courses/" + scenario.courseId(), new HttpHeaders(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> modules = listOf(response.getBody().get("modules"));
        assertThat(modules).hasSize(1);
        assertThat(listOf(modules.get(0).get("lessons"))).extracting(lesson -> lesson.get("lessonId"))
            .containsExactly(scenario.visible().toString());
        Map<?, ?> stats = (Map<?, ?>) response.getBody().get("stats");
        assertThat(stats.get("moduleCount")).isEqualTo(1);
        assertThat(stats.get("lessonCount")).isEqualTo(1);
        assertThat(stats.get("totalDuration")).isEqualTo("10m");
    }

    @Test
    void the_public_course_list_counts_only_the_lessons_with_video() {
        Scenario scenario = seedScenario();

        ResponseEntity<Map> response = call(HttpMethod.GET, "/api/v1/catalog/courses", new HttpHeaders(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> course = listOf(response.getBody().get("courses")).stream()
            .filter(candidate -> scenario.courseId().toString().equals(candidate.get("courseId")))
            .findFirst().orElseThrow();
        Map<?, ?> virtual = (Map<?, ?>) course.get("virtual");
        assertThat(virtual.get("moduleCount")).isEqualTo(1);
        assertThat(virtual.get("lessonCount")).isEqualTo(1);
        assertThat(virtual.get("totalDurationMinutes")).isEqualTo(10);
    }

    @Test
    void the_detail_and_the_stream_of_a_lesson_without_video_are_404_lesson_not_found_even_when_premium() {
        Scenario scenario = seedScenario();
        HttpHeaders unsubscribed = authHeadersFor(seedUser(Role.STUDENT), Role.STUDENT);

        for (UUID hidden : List.of(scenario.freeNoVideo(), scenario.premiumNoVideo(), scenario.blankVideo())) {
            for (HttpHeaders caller : List.of(new HttpHeaders(), unsubscribed)) {
                assertLessonNotFound(call(HttpMethod.GET, "/api/v1/virtual/lessons/" + hidden, caller, null));
                assertLessonNotFound(
                    call(HttpMethod.GET, "/api/v1/virtual/lessons/" + hidden + "/stream", caller, null)
                );
            }
        }
    }

    @Test
    void a_lesson_with_video_keeps_its_access_decision_and_a_missing_lesson_stays_404() {
        Scenario scenario = seedScenario();
        String stream = "/api/v1/virtual/lessons/" + scenario.visible() + "/stream";

        ResponseEntity<Map> anonymous = call(HttpMethod.GET, stream, new HttpHeaders(), null);
        ResponseEntity<Map> entitled = call(HttpMethod.GET, stream, entitledStudentFor(scenario.courseId()), null);
        ResponseEntity<Map> missing = call(
            HttpMethod.GET, "/api/v1/virtual/lessons/" + UUID.randomUUID() + "/stream", new HttpHeaders(), null
        );

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(entitled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertLessonNotFound(missing);
    }

    @Test
    void the_management_detail_still_shows_every_lesson_including_the_ones_without_video() {
        Scenario scenario = seedScenario();
        HttpHeaders admin = authHeadersFor(seedUser(Role.ADMIN), Role.ADMIN);

        ResponseEntity<Map> response =
            call(HttpMethod.GET, "/api/v1/admin/virtual/courses/" + scenario.courseId(), admin, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> modules = listOf(response.getBody().get("modules"));
        assertThat(modules).hasSize(2);
        assertThat(modules.stream().mapToInt(module -> listOf(module.get("lessons")).size()).sum()).isEqualTo(4);
    }

    @Test
    void the_progress_of_a_lesson_without_video_is_404_and_writes_nothing() {
        Scenario scenario = seedScenario();
        HttpHeaders student = entitledStudentFor(scenario.courseId());
        String freeProgress = "/api/v1/virtual/lessons/" + scenario.freeNoVideo() + "/progress";
        String premiumComplete = "/api/v1/virtual/lessons/" + scenario.premiumNoVideo() + "/complete";

        assertLessonNotFound(call(HttpMethod.GET, freeProgress, student, null));
        assertLessonNotFound(call(HttpMethod.PUT, freeProgress, student, Map.of("positionSeconds", 5)));
        assertLessonNotFound(call(HttpMethod.POST, premiumComplete, student, null));

        assertThat(progressRepository.count()).isZero();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void the_course_progress_denominator_leaves_out_the_lessons_without_video_so_it_reaches_100() {
        Scenario scenario = seedScenario();
        HttpHeaders student = entitledStudentFor(scenario.courseId());
        String courseProgress = "/api/v1/virtual/courses/" + scenario.courseId() + "/progress";
        String inProgress = "/api/v1/virtual/courses/me/in-progress";
        call(HttpMethod.PUT, "/api/v1/virtual/lessons/" + scenario.visible() + "/progress", student,
            Map.of("positionSeconds", 5));

        ResponseEntity<Map> started = call(HttpMethod.GET, courseProgress, student, null);
        ResponseEntity<List> startedList =
            http.exchange(inProgress, HttpMethod.GET, new HttpEntity<>(student), List.class);

        assertThat(started.getBody().get("totalLessons")).isEqualTo(1);
        assertThat(started.getBody().get("percentage")).isEqualTo(0);
        assertThat(startedList.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) startedList.getBody().get(0)).get("totalLessons")).isEqualTo(1);

        call(HttpMethod.POST, "/api/v1/virtual/lessons/" + scenario.visible() + "/complete", student, null);
        ResponseEntity<Map> finished = call(HttpMethod.GET, courseProgress, student, null);
        ResponseEntity<List> finishedList =
            http.exchange(inProgress, HttpMethod.GET, new HttpEntity<>(student), List.class);

        assertThat(finished.getBody().get("completedLessons")).isEqualTo(1);
        assertThat(finished.getBody().get("totalLessons")).isEqualTo(1);
        assertThat(finished.getBody().get("percentage")).isEqualTo(100);
        assertThat(finishedList.getBody()).isEmpty();
    }
}
