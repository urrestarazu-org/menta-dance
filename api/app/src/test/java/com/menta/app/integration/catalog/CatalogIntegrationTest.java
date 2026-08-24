package com.menta.app.integration.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL-backed HTTP integration coverage for the composed public catalog
 * (#95, #47): list composition, the virtual rich detail path, the 404
 * rules, and the unauthenticated-access requirement. The
 * "an owning module fails" case (#95 acceptance criteria) is covered with
 * mocked ports instead, in {@code CatalogControllerTest} — forcing a real
 * port to throw here would mean tearing down live infrastructure mid-test,
 * which buys nothing over a fast, deterministic unit test of the same
 * branch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class CatalogIntegrationTest {

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
    @Autowired private PhysicalCourseJpaRepository physicalCourseRepository;
    @Autowired private VirtualCourseJpaRepository virtualCourseRepository;
    @Autowired private VirtualModuleJpaRepository virtualModuleRepository;
    @Autowired private VirtualLessonJpaRepository virtualLessonRepository;

    // Every Redis-backed port needs a mock: integration-test excludes
    // RedisAutoConfiguration and this is one shared Spring context. Mirrors
    // BillingPlansIntegrationTest's exact mock set.
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
        virtualLessonRepository.deleteAll();
        virtualModuleRepository.deleteAll();
        physicalCourseRepository.deleteAll();
        virtualCourseRepository.deleteAll();
    }

    private UUID seedPhysicalCourse(String title, CourseStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        physicalCourseRepository.save(new PhysicalCourseJpaEntity(
            id, title, "desc " + title, UUID.randomUUID(), "María García", "TUESDAY", LocalTime.of(19, 0),
            60, "BEGINNER", 20, status, now, now
        ));
        return id;
    }

    private UUID seedVirtualCourse(String title, com.menta.virtual.domain.model.CourseStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        virtualCourseRepository.save(new VirtualCourseJpaEntity(
            id, title, "desc " + title, "descripción larga", UUID.randomUUID(), "https://cdn/img.jpg", "tango",
            "BEGINNER", false, status, now, now
        ));
        return id;
    }

    /**
     * Wired exactly the way {@code VirtualLessonJpaRepository.findByModuleIdOrderByDisplayOrderAsc}
     * expects, with a live Bunny.net-style id on every lesson — the test then
     * asserts the public detail endpoint NEVER leaks that id, which is the
     * core invariant of US-VIRTUAL-002 escenario 1.
     */
    private void seedVirtualModuleWithLessons(UUID courseId, UUID moduleId, int order, List<SeedLesson> lessons) {
        virtualModuleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo " + order, order));
        for (SeedLesson seed : lessons) {
            virtualLessonRepository.save(new VirtualLessonJpaEntity(
                seed.id, moduleId, courseId, seed.title, "desc " + seed.title,
                seed.videoId, seed.minutes, seed.free, seed.order
            ));
        }
    }

    private record SeedLesson(UUID id, String title, String videoId, int minutes, boolean free, int order) {
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_combines_a_physical_and_a_virtual_course() {
        UUID physicalId = seedPhysicalCourse("Salsa inicial", CourseStatus.ACTIVE);
        UUID virtualId = seedVirtualCourse("Tango Básico", com.menta.virtual.domain.model.CourseStatus.PUBLISHED);

        ResponseEntity<Map> response = http.exchange("/api/v1/catalog/courses", HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> courses = (List<Map<String, Object>>) response.getBody().get("courses");
        assertThat(courses).extracting(c -> c.get("courseId"))
            .containsExactlyInAnyOrder(physicalId.toString(), virtualId.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_an_unknown_course_id_returns_a_404_problem() {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/catalog/courses/" + UUID.randomUUID(), HttpMethod.GET, null, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_FOUND");
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_a_draft_virtual_course_returns_the_same_404_as_a_missing_one() {
        // Non-enumeration discipline — US-VIRTUAL-002 escenario 4. A
        // visitor must not be able to probe status.
        UUID id = seedVirtualCourse("Tango en borrador", com.menta.virtual.domain.model.CourseStatus.DRAFT);

        ResponseEntity<Map> response =
            http.exchange("/api/v1/catalog/courses/" + id, HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_FOUND");
    }

    @Test
    void the_public_catalog_endpoint_requires_no_authentication() {
        ResponseEntity<Map> response = http.exchange("/api/v1/catalog/courses", HttpMethod.GET, null, Map.class);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_resolves_a_virtual_course_with_modules_lessons_stats_and_no_video_leak() {
        UUID courseId = seedVirtualCourse("Tango Básico", com.menta.virtual.domain.model.CourseStatus.PUBLISHED);
        UUID moduleOne = UUID.randomUUID();
        seedVirtualModuleWithLessons(courseId, moduleOne, 1, List.of(
            new SeedLesson(UUID.randomUUID(), "Historia", "BUNNY-VIDEO-SECRET-1", 10, true, 1),
            new SeedLesson(UUID.randomUUID(), "Postura básica", "BUNNY-VIDEO-SECRET-2", 15, false, 2)
        ));

        ResponseEntity<Map> response =
            http.exchange("/api/v1/catalog/courses/" + courseId, HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();
        assertThat(body.get("courseId")).isEqualTo(courseId.toString());
        assertThat(body.get("title")).isEqualTo("Tango Básico");
        assertThat(body.get("category")).isEqualTo("tango");
        assertThat(body.get("level")).isEqualTo("BEGINNER");
        assertThat(body.get("isPremium")).isEqualTo(false);
        assertThat(body.get("thumbnailUrl")).isEqualTo("https://cdn/img.jpg");

        List<Map<String, Object>> modules = (List<Map<String, Object>>) body.get("modules");
        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).get("moduleId")).isEqualTo(moduleOne.toString());
        assertThat(modules.get(0).get("order")).isEqualTo(1);

        List<Map<String, Object>> lessons = (List<Map<String, Object>>) modules.get(0).get("lessons");
        assertThat(lessons).hasSize(2);
        assertThat(lessons.get(0).get("title")).isEqualTo("Historia");
        assertThat(lessons.get(0).get("duration")).isEqualTo("10:00");
        assertThat(lessons.get(0).get("isFree")).isEqualTo(true);
        assertThat(lessons.get(1).get("duration")).isEqualTo("15:00");
        assertThat(lessons.get(1).get("isFree")).isEqualTo(false);

        Map<String, Object> stats = (Map<String, Object>) body.get("stats");
        assertThat(stats.get("moduleCount")).isEqualTo(1);
        // aggregate lesson count for the seeded course is 2 (history + postura)
        assertThat(stats.get("lessonCount")).isEqualTo(2);
        assertThat(stats.get("totalDuration")).isEqualTo("25m");

        // No videoUrl / videoId anywhere on the wire — even though every
        // lesson in storage has one.
        assertThat(body).doesNotContainKey("videoUrl");
        assertThat(modules.get(0)).doesNotContainKey("videoUrl");
        assertThat(lessons.get(0)).doesNotContainKey("videoId");
        assertThat(lessons.get(0)).doesNotContainKey("videoUrl");
        assertThat(lessons.get(1)).doesNotContainKey("videoId");
        assertThat(lessons.get(1)).doesNotContainKey("videoUrl");
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_a_physical_only_course_answers_the_same_404_as_a_missing_one() {
        // #47 scope trade-off: physical detail is a follow-up. Until it
        // lands, a physical-only courseId answers 404 indistinguishable
        // from "no modality has it" — so a visitor cannot probe modality.
        UUID physicalId = seedPhysicalCourse("Salsa inicial", CourseStatus.ACTIVE);

        ResponseEntity<Map> response =
            http.exchange("/api/v1/catalog/courses/" + physicalId, HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_FOUND");
    }
}
