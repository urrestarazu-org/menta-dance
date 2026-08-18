package com.menta.app.integration.virtual;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL-backed coverage for {@link VirtualCourseCatalogPort} (US-VIRTUAL-001).
 * No HTTP: this module exposes no public endpoint of its own (see #46/#95) —
 * this test wires the real Spring context and calls the port bean directly,
 * proving the JPA layer, cursor pagination and count/duration aggregation
 * actually work against real MySQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class VirtualCourseCatalogIntegrationTest {

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

    @Autowired private VirtualCourseCatalogPort virtualCourseCatalogPort;
    @Autowired private VirtualCourseJpaRepository courseRepository;
    @Autowired private VirtualModuleJpaRepository moduleRepository;
    @Autowired private VirtualLessonJpaRepository lessonRepository;

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

    @AfterEach
    void cleanUp() {
        lessonRepository.deleteAll();
        moduleRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private UUID seedCourse(String title, CourseStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new VirtualCourseJpaEntity(
            id, title, "desc " + title, "https://cdn/img.jpg", "tango", "BEGINNER",
            false, status, now, now
        ));
        return id;
    }

    private void seedModuleWithLessons(UUID courseId, int lessonCount, int minutesEach) {
        UUID moduleId = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo"));
        for (int i = 0; i < lessonCount; i++) {
            lessonRepository.save(new VirtualLessonJpaEntity(
                UUID.randomUUID(), moduleId, courseId, "Lección " + i, minutesEach
            ));
        }
    }

    @Test
    void lists_only_published_courses() {
        UUID publishedId = seedCourse("Tango Básico", CourseStatus.PUBLISHED);
        seedCourse("Salsa Draft", CourseStatus.DRAFT);
        seedCourse("Vals Archivado", CourseStatus.ARCHIVED);

        List<VirtualCourseSummary> result = virtualCourseCatalogPort.listPublished(null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courseId()).isEqualTo(publishedId.toString());
        assertThat(result.get(0).title()).isEqualTo("Tango Básico");
    }

    @Test
    void returns_an_empty_list_when_there_are_no_published_courses() {
        seedCourse("Draft", CourseStatus.DRAFT);

        assertThat(virtualCourseCatalogPort.listPublished(null, 10)).isEmpty();
    }

    @Test
    void counts_modules_and_lessons_and_sums_duration_without_loading_their_content() {
        UUID courseId = seedCourse("Tango Básico", CourseStatus.PUBLISHED);
        seedModuleWithLessons(courseId, 3, 10);
        seedModuleWithLessons(courseId, 2, 15);

        VirtualCourseSummary summary = virtualCourseCatalogPort.listPublished(null, 10).get(0);

        assertThat(summary.moduleCount()).isEqualTo(2);
        assertThat(summary.lessonCount()).isEqualTo(5);
        assertThat(summary.totalDurationMinutes()).isEqualTo(3 * 10 + 2 * 15);
    }

    @Test
    void a_course_without_modules_reports_zero_counts_not_an_error() {
        seedCourse("Sin módulos", CourseStatus.PUBLISHED);

        VirtualCourseSummary summary = virtualCourseCatalogPort.listPublished(null, 10).get(0);

        assertThat(summary.moduleCount()).isZero();
        assertThat(summary.lessonCount()).isZero();
        assertThat(summary.totalDurationMinutes()).isZero();
    }

    @Test
    void find_published_by_id_returns_the_course_when_published() {
        UUID id = seedCourse("Tango Básico", CourseStatus.PUBLISHED);
        seedModuleWithLessons(id, 2, 10);

        assertThat(virtualCourseCatalogPort.findPublishedById(id.toString()))
            .isPresent()
            .get()
            .satisfies(course -> {
                assertThat(course.title()).isEqualTo("Tango Básico");
                assertThat(course.moduleCount()).isEqualTo(1);
                assertThat(course.lessonCount()).isEqualTo(2);
            });
    }

    @Test
    void find_published_by_id_returns_empty_when_the_course_is_a_draft() {
        UUID id = seedCourse("Salsa Draft", CourseStatus.DRAFT);

        assertThat(virtualCourseCatalogPort.findPublishedById(id.toString())).isEmpty();
    }

    @Test
    void find_published_by_id_returns_empty_when_the_course_does_not_exist() {
        assertThat(virtualCourseCatalogPort.findPublishedById(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void cursor_pagination_never_repeats_or_skips_a_course() {
        UUID first = seedCourse("A", CourseStatus.PUBLISHED);
        seedCourse("B", CourseStatus.PUBLISHED);
        seedCourse("C", CourseStatus.PUBLISHED);

        List<VirtualCourseSummary> firstPage = virtualCourseCatalogPort.listPublished(null, 1);
        assertThat(firstPage).hasSize(1);
        String cursor = firstPage.get(0).courseId();

        List<VirtualCourseSummary> secondPage = virtualCourseCatalogPort.listPublished(cursor, 10);

        assertThat(secondPage).hasSize(2);
        assertThat(secondPage).noneMatch(c -> c.courseId().equals(cursor));
    }
}
